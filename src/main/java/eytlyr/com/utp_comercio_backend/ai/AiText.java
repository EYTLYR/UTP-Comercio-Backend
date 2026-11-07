package eytlyr.com.utp_comercio_backend.ai;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.zone.StreetCode;

/** Textos en español que usa la IA para explicar de dónde sacó cada dato. */
public final class AiText {

	private AiText() {
	}

	public static String sourceName(SourceType type) {
		return switch (type) {
			case POST -> "publicación del muro";
			case SERVICE -> "oficio";
			case REVIEW -> "reseña";
			case RESOURCE_LISTING -> "listado de recursos";
		};
	}

	/** "hace 5 min", "hace 3 h", "hace 1 día", "hace 21 días". */
	public static String ago(OffsetDateTime when, OffsetDateTime now) {
		if (when == null) {
			return "";
		}
		Duration d = Duration.between(when, now);
		if (d.toMinutes() < 60) {
			return "hace " + Math.max(1, d.toMinutes()) + " min";
		}
		if (d.toHours() < 24) {
			return "hace " + d.toHours() + " h";
		}
		long days = d.toDays();
		return days == 1 ? "hace 1 día" : "hace " + days + " días";
	}

	/** Referencia de ubicación sin dirección exacta: la calle más cercana ("Jr. Los Pinos"). */
	public static String zoneLabel(List<StreetCode> streets, double lat, double lng) {
		return streets.stream()
				.min(Comparator.comparingDouble(s -> GeoUtils.haversineMeters(lat, lng, s.getLat(), s.getLng())))
				.map(StreetCode::getName)
				.orElse("la zona");
	}
}
