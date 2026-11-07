package eytlyr.com.utp_comercio_backend.emergency;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import eytlyr.com.utp_comercio_backend.TestAuth;
import eytlyr.com.utp_comercio_backend.TestAuth.Session;
import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FASE 8: privacidad del tablero, "Voy yo", resumen de recursos y capas del mapa. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EmergencyFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void cleanOtps() {
		jdbc.update("delete from otp_code");
	}

	@AfterEach
	void restoreReport() {
		jdbc.update("update vulnerable_report set status = 'PENDING', assigned_to_id = null, assigned_label = null "
				+ "where address_text = 'Mz. C lote 12'");
	}

	@Test
	void neighborsSeeApproximateAddressesAndCanSayVoyYo() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		Session brigade = TestAuth.login(mvc, "999000111");

		mvc.perform(get("/api/v1/vulnerable-reports").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.content[0].priority").value("HIGH"))
				.andExpect(jsonPath("$.content[*].addressText", hasItems("Mz. C", "Jr. Los Pinos")))
				.andExpect(jsonPath("$.content[*].addressText", not(hasItem("Mz. C lote 12"))))
				.andExpect(jsonPath("$.content[*].exactLocation", not(hasItem(true))));
		mvc.perform(get("/api/v1/vulnerable-reports").header("Authorization", brigade.bearer()))
				.andExpect(jsonPath("$.content[*].addressText", hasItems("Mz. C lote 12", "Jr. Los Pinos 340")));

		UUID teresa = jdbc.queryForObject("select id from vulnerable_report where address_text = 'Mz. C lote 12'",
				UUID.class);
		mvc.perform(post("/api/v1/vulnerable-reports/" + teresa + "/assign").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ON_THE_WAY"))
				.andExpect(jsonPath("$.assignedLabel").value("María Quispe"))
				.andExpect(jsonPath("$.assignedToMe").value(true));
	}

	@Test
	void resourceSummaryAndMapLayers() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		mvc.perform(get("/api/v1/resources/summary").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$[0].type").value("WATER"))
				.andExpect(jsonPath("$[0].label").value("Agua"))
				.andExpect(jsonPath("$[0].have").value(14))
				.andExpect(jsonPath("$[0].need").value(9));

		mvc.perform(get("/api/v1/map/pins").param("layer", "daily").param("radiusM", "5000")
						.header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$[*].kind", hasItems("svc", "need", "offer")))
				.andExpect(jsonPath("$[*].icon", hasItem("plumbing")));
		mvc.perform(get("/api/v1/map/pins").param("layer", "emergency").param("radiusM", "5000")
						.header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$[*].kind", hasItems("vuln", "safe", "res", "group")));
		mvc.perform(get("/api/v1/map/pins").param("layer", "prevention").param("radiusM", "5000")
						.header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$[*].kind", hasItems("risk", "mitig", "safe")))
				.andExpect(jsonPath("$[*].icon", hasItem("landslide")));
	}
}
