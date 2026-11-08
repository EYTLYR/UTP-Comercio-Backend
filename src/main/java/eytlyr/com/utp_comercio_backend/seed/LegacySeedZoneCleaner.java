package eytlyr.com.utp_comercio_backend.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Antes la app creaba al arrancar una zona de ejemplo («Zona 3 · Las Flores») con calles y puntos seguros
 * inventados. Ya no: las zonas las crean los vecinos y las Municipalidades. Esto borra esa zona de las bases
 * que ya la tienen, pero solo cuando nadie la usa (ni vecinos ni publicaciones); si aún tiene datos reales,
 * la deja y lo intenta en el próximo arranque.
 */
@Component
@Profile("!dev & !demo")
public class LegacySeedZoneCleaner implements CommandLineRunner {

	public static final String LEGACY_ZONE = "Zona 3 · Las Flores";

	private static final Logger log = LoggerFactory.getLogger(LegacySeedZoneCleaner.class);

	private final JdbcTemplate jdbc;

	private final TransactionTemplate tx;

	public LegacySeedZoneCleaner(JdbcTemplate jdbc, TransactionTemplate tx) {
		this.jdbc = jdbc;
		this.tx = tx;
	}

	/**
	 * Un vecino que quedó en la zona de ejemplo pasa a su zona real: lo que publicó se va con él, así la zona
	 * de ejemplo queda vacía y se borra en el próximo arranque.
	 */
	public static void moveUserContent(JdbcTemplate jdbc, java.util.UUID userId, java.util.UUID from,
			java.util.UUID to) {
		String[][] owned = { { "post", "author_id" }, { "sos_alert", "user_id" }, { "check_in", "user_id" },
				{ "capability", "user_id" }, { "vulnerable_report", "reporter_id" }, { "resource_listing", "user_id" },
				{ "risk_report", "reporter_id" } };
		for (String[] t : owned) {
			jdbc.update("update " + t[0] + " set zone_id = ? where zone_id = ? and " + t[1] + " = ?", to, from, userId);
		}
	}

	@Override
	public void run(String... args) {
		try {
			Integer deleted = tx.execute(status -> {
				var ids = jdbc.queryForList("select id from zone where name = ?", Object.class, LEGACY_ZONE);
				if (ids.isEmpty()) {
					return 0;
				}
				Object id = ids.get(0);
				Integer users = jdbc.queryForObject("select count(*) from app_user where zone_id = ?", Integer.class, id);
				if (users != null && users > 0) {
					return -1;
				}
				jdbc.update("delete from street_code where zone_id = ?", id);
				jdbc.update("delete from safe_point where zone_id = ?", id);
				jdbc.update("delete from rescue_plan where zone_id = ?", id);
				return jdbc.update("delete from zone where id = ?", id);
			});
			if (deleted != null && deleted > 0) {
				log.info("Se borró la zona de ejemplo «{}».", LEGACY_ZONE);
			}
			else if (deleted != null && deleted < 0) {
				log.info("La zona de ejemplo «{}» aún tiene vecinos: se borrará cuando se muden a su zona real.",
						LEGACY_ZONE);
			}
		}
		catch (RuntimeException e) {
			// Tiene publicaciones u otros datos reales: se queda por ahora.
			log.info("La zona de ejemplo «{}» aún tiene datos y no se borró.", LEGACY_ZONE);
		}
	}
}
