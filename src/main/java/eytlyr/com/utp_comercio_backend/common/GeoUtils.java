package eytlyr.com.utp_comercio_backend.common;

import java.util.concurrent.ThreadLocalRandom;

/** Distancias sobre la Tierra sin PostGIS: recuadro para filtrar en SQL y Haversine para el valor exacto. */
public final class GeoUtils {

	public static final double EARTH_RADIUS_M = 6_371_008.8;

	private static final double METERS_PER_DEGREE_LAT = 111_320.0;

	private GeoUtils() {
	}

	/** Caja [minLat, maxLat, minLng, maxLng] que contiene el círculo de radio radiusM (para filtrar en SQL). */
	public static double[] box(double lat, double lng, double radiusM) {
		double dLat = radiusM / 111_320d;
		double dLng = radiusM / (111_320d * Math.max(0.01, Math.cos(Math.toRadians(lat))));
		return new double[] { lat - dLat, lat + dLat, lng - dLng, lng + dLng };
	}

	public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
	}

	/** Recuadro que contiene el círculo de radio radiusM alrededor del punto. */
	public static BoundingBox boundingBox(double lat, double lng, double radiusM) {
		double dLat = radiusM / METERS_PER_DEGREE_LAT;
		double cos = Math.max(Math.cos(Math.toRadians(lat)), 1e-6);
		double dLng = radiusM / (METERS_PER_DEGREE_LAT * cos);
		return new BoundingBox(lat - dLat, lat + dLat, lng - dLng, lng + dLng);
	}

	/** Coordenada aproximada (3 decimales, unos 100 m) para no revelar la dirección exacta. */
	public static double approx(double coordinate) {
		return Math.round(coordinate * 1000.0) / 1000.0;
	}

	/**
	 * Geofuzzing (ofuscación espacial): aplica un desplazamiento aleatorio (offset)
	 * de entre 100 y 300 metros a una latitud y longitud para proteger la privacidad del usuario.
	 *
	 * @param lat Latitud original
	 * @param lng Longitud original
	 * @return double[] con [latitudOfuscada, longitudOfuscada]
	 */
	public static double[] geofuzz(double lat, double lng) {
		double offsetM = 100.0 + ThreadLocalRandom.current().nextDouble() * 200.0;
		double angle = ThreadLocalRandom.current().nextDouble() * 2.0 * Math.PI;
		double dLat = (offsetM * Math.cos(angle)) / METERS_PER_DEGREE_LAT;
		double cosLat = Math.max(Math.cos(Math.toRadians(lat)), 1e-6);
		double dLng = (offsetM * Math.sin(angle)) / (METERS_PER_DEGREE_LAT * cosLat);
		return new double[] { lat + dLat, lng + dLng };
	}

	/**
	 * Versión null-safe de geofuzz para objetos Double.
	 */
	public static Double[] geofuzz(Double lat, Double lng) {
		if (lat == null || lng == null) {
			return new Double[] { null, null };
		}
		double[] fuzzed = geofuzz(lat.doubleValue(), lng.doubleValue());
		return new Double[] { fuzzed[0], fuzzed[1] };
	}

	public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {

		public boolean contains(double lat, double lng) {
			return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
		}
	}
}
