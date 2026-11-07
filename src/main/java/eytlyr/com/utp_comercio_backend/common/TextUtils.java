package eytlyr.com.utp_comercio_backend.common;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Utilidades de texto en español: búsqueda sin tildes, iniciales y celular enmascarado. */
public final class TextUtils {

	/** Sufijos que se quitan para que "gasfitero" encuentre "Gasfitería" o "carpintero" a "Carpintería". */
	private static final List<String> SUFFIXES = List.of("erias", "eria", "eros", "eras", "ero", "era", "ias", "ia",
			"os", "as", "es", "o", "a", "s");

	private TextUtils() {
	}

	/** Minúsculas y sin tildes: "Gasfitería" → "gasfiteria". */
	public static String normalize(String s) {
		if (s == null) {
			return "";
		}
		String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return n.toLowerCase(Locale.ROOT).trim();
	}

	/** Raíz simple de una palabra (mínimo 4 letras). */
	public static String stem(String word) {
		String w = normalize(word);
		for (String suffix : SUFFIXES) {
			if (w.endsWith(suffix) && w.length() - suffix.length() >= 4) {
				return w.substring(0, w.length() - suffix.length());
			}
		}
		return w;
	}

	/** true si cada palabra de la búsqueda (por su raíz) aparece en alguno de los textos. */
	public static boolean matches(String query, String... texts) {
		if (query == null || query.isBlank()) {
			return true;
		}
		String haystack = normalize(String.join(" ", Arrays.stream(texts).map(t -> t == null ? "" : t).toList()));
		return Arrays.stream(normalize(query).split("\\s+"))
				.filter(w -> !w.isBlank())
				.allMatch(w -> haystack.contains(stem(w)));
	}

	/** "María Quispe" → "MQ". */
	public static String initials(String name) {
		if (name == null || name.isBlank()) {
			return "?";
		}
		StringBuilder sb = new StringBuilder();
		for (String part : name.trim().split("\\s+")) {
			if (!part.isEmpty() && Character.isLetter(part.charAt(0)) && sb.length() < 2) {
				sb.append(Character.toUpperCase(part.charAt(0)));
			}
		}
		return sb.isEmpty() ? "?" : sb.toString();
	}

	/** "987654321" → "987 *** 321". */
	public static String maskPhone(String phone) {
		if (phone == null || phone.length() < 6) {
			return "***";
		}
		return phone.substring(0, 3) + " *** " + phone.substring(phone.length() - 3);
	}
}
