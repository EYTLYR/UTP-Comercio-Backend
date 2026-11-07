package eytlyr.com.utp_comercio_backend.sms;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.sos.SosType;
import eytlyr.com.utp_comercio_backend.zone.StreetCode;

/**
 * SOS comprimido para SMS o sonido (ANEXO C): NB#SOS!{T}{n}@{LOC}$P:{ALTA|MEDIA}, máximo 25 caracteres.
 * Ejemplo: NB#SOS!H1@LP340$P:ALTA = 1 herido en Jr. Los Pinos 340, prioridad alta.
 */
@Component
public class SosCodec {

	public static final String PREFIX = "NB#SOS!";

	public static final int MAX_LENGTH = 25;

	public static final Pattern CODE = Pattern.compile("^NB#SOS!([A-Z])(\\d)@([A-Z]+?)(\\d+)\\$P:(ALTA|MEDIA)$");

	/** Para buscar un código dentro de un SMS con más texto. */
	public static final Pattern CODE_IN_TEXT = Pattern.compile("NB#SOS![A-Z]\\d@[A-Z]+\\d+\\$P:(?:ALTA|MEDIA)");

	private static final Pattern BLOCK = Pattern.compile("(?i)(?:mz\\.?|manzana)\\s*([a-z])\\s*(?:lote|lt\\.?)\\s*(\\d+)");

	private static final Pattern NUMBER = Pattern.compile("(\\d{1,4})");

	/** Letra → tipo y nombre en español. Las de reportes vulnerables y las del botón SOS. */
	public record Kind(char letter, VulnerableCategory category, SosType sosType, String label) {
	}

	static final Map<Character, Kind> KINDS = Map.ofEntries(
			Map.entry('H', new Kind('H', VulnerableCategory.INJURED, null, "Herido")),
			Map.entry('A', new Kind('A', VulnerableCategory.TRAPPED, null, "Atrapado")),
			Map.entry('M', new Kind('M', VulnerableCategory.ELDERLY_ALONE, null, "Adulto mayor")),
			Map.entry('D', new Kind('D', VulnerableCategory.DISABILITY, null, "Persona con discapacidad")),
			Map.entry('N', new Kind('N', VulnerableCategory.CHILDREN_ALONE, null, "Niños solos")),
			Map.entry('G', new Kind('G', VulnerableCategory.PREGNANT, null, "Gestante")),
			Map.entry('O', new Kind('O', VulnerableCategory.OXYGEN_DEPENDENT, null, "Depende de oxígeno")),
			Map.entry('S', new Kind('S', null, SosType.HEALTH, "Salud")),
			Map.entry('F', new Kind('F', null, SosType.FIRE, "Incendio")),
			Map.entry('R', new Kind('R', null, SosType.ROBBERY, "Robo")),
			Map.entry('Q', new Kind('Q', null, SosType.EARTHQUAKE, "Sismo")),
			Map.entry('V', new Kind('V', null, SosType.VIOLENCE, "Violencia")),
			Map.entry('X', new Kind('X', null, SosType.OTHER, "Otra emergencia")));

	/** Resultado de decodificar. lat/lng son null para manzana y lote (no hay coordenadas en la tabla). */
	public record Decoded(String code, Kind kind, int people, String locationCode, String addressText, Double lat,
			Double lng, boolean high) {
	}

	public static char letter(SosType type) {
		if (type == null) {
			return 'X';
		}
		return switch (type) {
			case HEALTH -> 'S';
			case FIRE -> 'F';
			case ROBBERY -> 'R';
			case EARTHQUAKE -> 'Q';
			case VIOLENCE -> 'V';
			case OTHER -> 'X';
		};
	}

	public static char letter(VulnerableCategory category) {
		return switch (category) {
			case INJURED -> 'H';
			case TRAPPED -> 'A';
			case ELDERLY_ALONE -> 'M';
			case DISABILITY -> 'D';
			case CHILDREN_ALONE -> 'N';
			case PREGNANT -> 'G';
			case OXYGEN_DEPENDENT -> 'O';
		};
	}

	/** @throws IllegalArgumentException si el código supera 25 caracteres */
	public String encode(char typeLetter, int people, String location, boolean high) {
		int n = Math.max(1, Math.min(people, 9));
		String code = PREFIX + typeLetter + n + "@" + location + "$P:" + (high ? "ALTA" : "MEDIA");
		if (code.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("El código SOS supera " + MAX_LENGTH + " caracteres: " + code);
		}
		return code;
	}

	/** Sin dirección: código de la calle más cercana y número 0 (ANEXO C). */
	public String nearestLocation(List<StreetCode> streets, double lat, double lng) {
		return streets.stream()
				.min(Comparator.comparingDouble(s -> GeoUtils.haversineMeters(lat, lng, s.getLat(), s.getLng())))
				.map(s -> s.getCode() + "0")
				.orElse("X0");
	}

	/**
	 * Dirección escrita → código de ubicación: "Jr. Los Pinos 340" → "LP340", "Mz. C lote 12" → "MC12".
	 * Si no la reconoce, devuelve vacío.
	 */
	public Optional<String> locationFromAddress(List<StreetCode> streets, String address) {
		if (address == null || address.isBlank()) {
			return Optional.empty();
		}
		Matcher block = BLOCK.matcher(address);
		if (block.find()) {
			return Optional.of("M" + block.group(1).toUpperCase() + block.group(2));
		}
		String normalized = TextUtils.normalize(address);
		for (StreetCode s : streets) {
			String bare = TextUtils.normalize(s.getName()).replaceFirst("^(jr|av|pje|psje|calle|ca)\\.?\\s+", "");
			if (normalized.contains(bare)) {
				Matcher number = NUMBER.matcher(normalized.substring(normalized.indexOf(bare) + bare.length()));
				return Optional.of(s.getCode() + (number.find() ? number.group(1) : "0"));
			}
		}
		return Optional.empty();
	}

	/** Código de un SOS del botón: una persona, prioridad alta. */
	public String forSos(SosType type, List<StreetCode> streets, double lat, double lng) {
		return encode(letter(type), 1, nearestLocation(streets, lat, lng), true);
	}

	/**
	 * Decodifica con las calles de la zona. Un prefijo de 2 letras que empieza con M y no es una calle
	 * se interpreta como manzana ("MC12" = Mz. C lote 12).
	 * @return vacío si el código no es válido
	 */
	public Optional<Decoded> decode(String code, List<StreetCode> streets) {
		if (code == null) {
			return Optional.empty();
		}
		String c = code.trim().toUpperCase();
		Matcher m = CODE.matcher(c);
		if (c.length() > MAX_LENGTH || !m.matches()) {
			return Optional.empty();
		}
		Kind kind = KINDS.get(m.group(1).charAt(0));
		if (kind == null) {
			return Optional.empty();
		}
		int people = Integer.parseInt(m.group(2));
		String prefix = m.group(3);
		String number = m.group(4);
		boolean high = "ALTA".equals(m.group(5));
		Optional<StreetCode> street = streets.stream().filter(s -> s.getCode().equals(prefix)).findFirst();
		if (street.isPresent()) {
			StreetCode s = street.get();
			String address = "0".equals(number) ? s.getName() : s.getName() + " " + Integer.parseInt(number);
			return Optional.of(new Decoded(c, kind, people, prefix + number, address, s.getLat(), s.getLng(), high));
		}
		if (prefix.length() == 2 && prefix.charAt(0) == 'M') {
			String address = "Mz. " + prefix.charAt(1) + " lote " + Integer.parseInt(number);
			return Optional.of(new Decoded(c, kind, people, prefix + number, address, null, null, high));
		}
		return Optional.empty();
	}
}
