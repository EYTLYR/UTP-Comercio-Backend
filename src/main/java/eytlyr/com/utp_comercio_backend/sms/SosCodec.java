package eytlyr.com.utp_comercio_backend.sms;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.sos.SosType;
import eytlyr.com.utp_comercio_backend.zone.StreetCode;

/**
 * SOS comprimido para SMS o sonido (ANEXO C): NB#SOS!{T}{n}@{LOC}$P:{ALTA|MEDIA}, máximo 25 caracteres.
 * Ejemplo: NB#SOS!H1@LP340$P:ALTA = 1 herido en Jr. Los Pinos 340, prioridad alta.
 * La decodificación y la entrada por SMS se completan en la FASE 9.
 */
@Component
public class SosCodec {

	public static final String PREFIX = "NB#SOS!";

	public static final int MAX_LENGTH = 25;

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

	/** Código de un SOS del botón: una persona, prioridad alta. */
	public String forSos(SosType type, List<StreetCode> streets, double lat, double lng) {
		return encode(letter(type), 1, nearestLocation(streets, lat, lng), true);
	}
}
