package eytlyr.com.utp_comercio_backend.user;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/** PostGIS: la columna location se genera desde lat/lng y ST_DWithin mide en metros. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GeoRadiusQueryTest {

	// Lejos de los datos de ejemplo para no mezclarse con ellos.
	static final double LAT = -12.5000;

	static final double LNG = -76.5000;

	@Autowired
	AppUserRepository users;

	@Test
	void findsNeighborsWithinRadiusOrderedByDistance() {
		// Hacia el norte, 0.001° de latitud son unos 110.6 m.
		AppUser here = save("910000001", 0);
		AppUser m300 = save("910000002", 0.0027); // ~299 m
		AppUser m487 = save("910000003", 0.0044); // ~487 m
		AppUser m520 = save("910000004", 0.0047); // ~520 m
		AppUser m664 = save("910000005", 0.0060); // ~664 m
		save("910000006", 0.0200); // ~2.2 km

		assertThat(here.getLocation()).isNotNull();
		assertThat(here.getLocation().getSRID()).isEqualTo(4326);
		assertThat(here.getLocation().getX()).isEqualTo(LNG); // x = longitud
		assertThat(here.getLocation().getY()).isEqualTo(LAT);

		assertThat(ids(users.findWithinRadius(LAT, LNG, 500)))
			.containsExactly(here.getId(), m300.getId(), m487.getId());
		assertThat(ids(users.findInRing(LAT, LNG, 500, 1000)))
			.containsExactlyInAnyOrder(m520.getId(), m664.getId());
	}

	@Test
	void userWithoutLocationIsNeverReturned() {
		AppUser nowhere = users.saveAndFlush(AppUser.builder().phone("910000009").name("Sin ubicación").build());
		assertThat(nowhere.getLocation()).isNull();
		assertThat(ids(users.findWithinRadius(LAT, LNG, 100_000))).doesNotContain(nowhere.getId());
	}

	private AppUser save(String phone, double northDegrees) {
		return users.saveAndFlush(AppUser.builder()
			.phone(phone)
			.name("Vecino " + phone)
			.lat(LAT + northDegrees)
			.lng(LNG)
			.build());
	}

	private static List<java.util.UUID> ids(List<AppUser> list) {
		return list.stream().map(AppUser::getId).toList();
	}
}
