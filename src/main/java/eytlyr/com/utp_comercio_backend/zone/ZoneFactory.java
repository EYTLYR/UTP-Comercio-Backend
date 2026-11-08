package eytlyr.com.utp_comercio_backend.zone;

import java.util.Comparator;
import java.util.Locale;

import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.common.GeoUtils;

/**
 * Zonas reales, sin zonas fijas: si ningún barrio registrado cubre la casa de un vecino, se crea uno nuevo con el
 * nombre de su urbanización o barrio (lo resuelve la app con OpenStreetMap). La Municipalidad también puede crear
 * zonas de su distrito desde su panel.
 */
@Component
public class ZoneFactory {

	/** Radio de una zona creada por un vecino: más o menos un barrio o urbanización. */
	public static final int DEFAULT_RADIUS_M = 1200;

	private final ZoneRepository zones;

	public ZoneFactory(ZoneRepository zones) {
		this.zones = zones;
	}

	/**
	 * La zona que cubre el punto (si la actual lo cubre, se queda en ella); si ninguna lo cubre, crea una nueva
	 * centrada en el punto.
	 */
	public Zone coveringOrCreate(double lat, double lng, Zone current, String barrio, String district) {
		if (current != null && covers(current, lat, lng)) {
			return current;
		}
		return zones.findAll().stream()
				.filter(z -> covers(z, lat, lng))
				.min(Comparator.comparingDouble(z -> GeoUtils.haversineMeters(lat, lng, z.getCenterLat(), z.getCenterLng())))
				.orElseGet(() -> create(barrio, district, lat, lng, DEFAULT_RADIUS_M));
	}

	public Zone create(String name, String district, double lat, double lng, int radiusM) {
		String cleanDistrict = clean(district);
		String base = clean(name);
		if (base == null) {
			base = cleanDistrict != null ? "Zona de " + cleanDistrict
					: String.format(Locale.ROOT, "Zona %.3f, %.3f", lat, lng);
		}
		Zone z = Zone.builder()
				.name(uniqueName(base, cleanDistrict))
				.district(cleanDistrict != null ? cleanDistrict : "Sin distrito")
				.centerLat(lat)
				.centerLng(lng)
				.radiusM(Math.max(200, Math.min(radiusM, 10_000)))
				.mode(ZoneMode.NORMAL)
				.build();
		// Se guarda de inmediato: luego se mueven filas a esta zona con SQL directo.
		return zones.saveAndFlush(z);
	}

	public static boolean covers(Zone z, double lat, double lng) {
		return GeoUtils.haversineMeters(lat, lng, z.getCenterLat(), z.getCenterLng()) <= z.getRadiusM();
	}

	/** "Urb. Mangomarca" ya existe → "Urb. Mangomarca · San Juan de Lurigancho" → "… 2". */
	private String uniqueName(String base, String district) {
		String name = truncate(base);
		if (!zones.existsByName(name)) {
			return name;
		}
		if (district != null && !base.contains(district)) {
			String withDistrict = truncate(base + " · " + district);
			if (!zones.existsByName(withDistrict)) {
				return withDistrict;
			}
			base = withDistrict;
		}
		for (int i = 2;; i++) {
			String candidate = truncate(base) + " " + i;
			if (!zones.existsByName(candidate)) {
				return candidate;
			}
		}
	}

	private static String truncate(String s) {
		return s.length() <= 110 ? s : s.substring(0, 110).trim();
	}

	private static String clean(String s) {
		return s == null || s.isBlank() ? null : s.trim().replaceAll("\\s+", " ");
	}
}
