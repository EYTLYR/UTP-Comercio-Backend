package eytlyr.com.utp_comercio_backend.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Los datos del ANEXO A quedan cargados en dev y el seeder es idempotente. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DemoDataSeederTest {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	DemoDataSeeder seeder;

	@Test
	void annexDataIsLoaded() {
		assertThat(count("zone")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select mode from zone where name = ?", String.class, DemoDataSeeder.ZONE_NAME))
				.isEqualTo("NORMAL");
		assertThat(count("street_code")).isEqualTo(5);
		assertThat(count("app_user")).isEqualTo(19);
		assertThat(count("trusted_contact")).isEqualTo(3);
		assertThat(count("medical_card")).isEqualTo(1);
		assertThat(count("neighbor_service")).isEqualTo(7);
		assertThat(count("review")).isEqualTo(4);
		assertThat(count("post")).isEqualTo(15);
		assertThat(count("vulnerable_report")).isEqualTo(4);
		assertThat(count("support_group")).isEqualTo(5);
		assertThat(count("safe_point")).isEqualTo(2);
		assertThat(count("risk_report")).isEqualTo(7);
	}

	@Test
	void rolesAndEndorsementCountersMatchThePrototype() {
		assertThat(jdbc.queryForObject("select role from app_user where phone = ?", String.class, "999000111"))
				.isEqualTo("BRIGADIST");
		assertThat(jdbc.queryForObject("select role from app_user where phone = ?", String.class, "999000000"))
				.isEqualTo("ADMIN");
		assertThat(jdbc.queryForObject("select endorsement_count from neighbor_service where title = ?",
				Integer.class, "Gasfitería")).isEqualTo(48);
	}

	@Test
	void resourceSummariesMatchTheAnnex() {
		assertThat(sum("resource_type", "WATER", "HAVE")).isEqualTo(14);
		assertThat(sum("resource_type", "WATER", "NEED")).isEqualTo(9);
		assertThat(sum("resource_type", "ENERGY", "NEED")).isEqualTo(15);
		assertThat(sum("mitigation_type", "SANDBAGS", "HAVE")).isEqualTo(40);
		assertThat(sum("mitigation_type", "SANDBAGS", "NEED")).isEqualTo(120);
		assertThat(sum("mitigation_type", "SHOVELS", "HAVE")).isEqualTo(9);
	}

	@Test
	void historyIsInThePastAndCoordinatesUseTheAnnexFormula() {
		assertThat(jdbc.queryForObject(
				"select count(*) from post where created_at < now() - interval '100 days'", Integer.class)).isEqualTo(1);
		Double lat = jdbc.queryForObject("select lat from vulnerable_report where address_text = ?", Double.class,
				"Mz. C lote 12");
		assertThat(lat).isCloseTo(-11.9790 + (50 - 34) * 0.000108, within(1e-9));
	}

	@Test
	void runningTwiceDoesNotDuplicate() throws Exception {
		seeder.run();
		assertThat(count("app_user")).isEqualTo(19);
		assertThat(count("zone")).isEqualTo(1);
	}

	private int count(String table) {
		return jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	private int sum(String column, String type, String kind) {
		return jdbc.queryForObject("select coalesce(sum(quantity), 0) from resource_listing where " + column
				+ " = ? and kind = ?", Integer.class, type, kind);
	}
}
