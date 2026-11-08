package eytlyr.com.utp_comercio_backend.emergency;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import eytlyr.com.utp_comercio_backend.TestAuth;
import eytlyr.com.utp_comercio_backend.TestAuth.Session;
import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;
import eytlyr.com.utp_comercio_backend.seed.DemoDataSeeder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La app muestra quién activó la emergencia, desde qué hora y por qué, y cuántos vecinos dijeron «Estoy bien»
 * desde que empezó. Vecino 900000601.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EmergencyInfoFlowTest {

	static final String NEIGHBOR = "900000601";

	static final String PLATFORM_ADMIN = "900000901";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	UUID zoneId;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from otp_code");
		zoneId = jdbc.queryForObject("select id from zone where name = ?", UUID.class, DemoDataSeeder.ZONE_NAME);
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("update zone set mode = 'NORMAL', mode_source = null, mode_reason = null, mode_activated_at = null "
				+ "where id = ?", zoneId);
		jdbc.update("delete from check_in where user_id in (select id from app_user where phone like '9000006%')");
		jdbc.update("delete from app_user where phone like '9000006%'");
	}

	@Test
	void emergencyShowsReasonTimeAndSafeNeighbors() throws Exception {
		Session admin = TestAuth.login(mvc, PLATFORM_ADMIN);
		mvc.perform(post("/api/v1/admin/zones/" + zoneId + "/mode").header("Authorization", admin.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mode\":\"EMERGENCY\",\"reason\":\"Sismo de magnitud 6\"}"))
				.andExpect(status().isOk());

		Session neighbor = TestAuth.login(mvc, NEIGHBOR);
		jdbc.update("update app_user set zone_id = ? where phone = ?", zoneId, NEIGHBOR);
		mvc.perform(get("/api/v1/me").header("Authorization", neighbor.bearer()))
				.andExpect(jsonPath("$.zone.modeSource").value("MUNICIPALITY"))
				.andExpect(jsonPath("$.zone.modeReason").value("Sismo de magnitud 6"))
				.andExpect(jsonPath("$.zone.modeActivatedAt").exists());

		long before = jdbc.queryForObject("select count(distinct user_id) from check_in where zone_id = ? "
				+ "and status = 'SAFE' and created_at > (select mode_activated_at from zone where id = ?)", Long.class,
				zoneId, zoneId);
		mvc.perform(post("/api/v1/check-ins").header("Authorization", neighbor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SAFE\"}"))
				.andExpect(status().isCreated());
		mvc.perform(get("/api/v1/zones/" + zoneId + "/dashboard").header("Authorization", neighbor.bearer()))
				.andExpect(jsonPath("$.safeCheckIns").value(before + 1));
	}
}
