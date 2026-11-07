package eytlyr.com.utp_comercio_backend.seed;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.emergency.SafePoint;
import eytlyr.com.utp_comercio_backend.emergency.SafePointType;
import eytlyr.com.utp_comercio_backend.zone.StreetCode;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;
import eytlyr.com.utp_comercio_backend.zone.ZoneRepository;

/**
 * Datos base (no son datos de prueba): si la base no tiene ninguna zona, crea la Zona 3 · Las Flores con
 * sus calles (para los códigos SMS) y sus puntos seguros. Sin zona nadie podría registrarse
 * ("Nearby aún no llega a tu zona"). No crea personas, publicaciones ni reportes: esos los crean los
 * vecinos reales al usar la app. Corre en todos los perfiles y no hace nada si ya hay zonas.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class BaseDataInitializer implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(BaseDataInitializer.class);

	public static final String ZONE_NAME = "Zona 3 · Las Flores";

	private final ZoneRepository zones;

	private final EntityManager em;

	public BaseDataInitializer(ZoneRepository zones, EntityManager em) {
		this.zones = zones;
		this.em = em;
	}

	/** Cuadrícula del mapa de la app (x,y de 0 a 100, unos 12 m por unidad) → latitud. */
	static double lat(double y) {
		return -11.9790 + (50 - y) * 0.000108;
	}

	/** Cuadrícula del mapa de la app → longitud. */
	static double lng(double x) {
		return -77.0030 + (x - 50) * 0.000110;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (zones.count() > 0) {
			return;
		}
		Zone zone = Zone.builder()
				.name(ZONE_NAME)
				.district("San Juan de Lurigancho")
				.centerLat(lat(50))
				.centerLng(lng(50))
				.radiusM(1500)
				.mode(ZoneMode.NORMAL)
				.build();
		em.persist(zone);
		street(zone, "LP", "Jr. Los Pinos", 64, 61);
		street(zone, "LF", "Jr. Las Flores", 50, 50);
		street(zone, "LR", "Pje. Las Rosas", 40, 74);
		street(zone, "AP", "Av. Principal", 80, 36);
		street(zone, "QC", "Quebrada Canto Grande", 18, 20);
		em.persist(SafePoint.builder().zone(zone).name("Punto de reunión · Parque Las Flores")
				.type(SafePointType.MEETING_POINT).lat(lat(52)).lng(lng(24)).build());
		em.persist(SafePoint.builder().zone(zone).name("Refugio · Local comunal").type(SafePointType.SHELTER)
				.capacity(60).occupied(0).lat(lat(76)).lng(lng(80)).build());
		log.info("Datos base creados: {} con 5 calles y 2 puntos seguros (sin usuarios).", ZONE_NAME);
	}

	private void street(Zone zone, String code, String name, double x, double y) {
		em.persist(StreetCode.builder().zone(zone).code(code).name(name).lat(lat(y)).lng(lng(x)).build());
	}
}
