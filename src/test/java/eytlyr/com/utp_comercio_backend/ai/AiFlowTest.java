package eytlyr.com.utp_comercio_backend.ai;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FASE 9: inventario aprendido de los datos semilla, plan de rescate, borrado respetado y SMS entrante. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AiFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void cleanOtps() {
		jdbc.update("delete from otp_code");
	}

	@AfterEach
	void restore() {
		jdbc.update("delete from mission_assignment");
		jdbc.update("delete from rescue_plan");
		jdbc.update("delete from vulnerable_report where source <> 'USER'");
		jdbc.update("update vulnerable_report set status = 'PENDING', assigned_to_id = null, assigned_label = null "
				+ "where address_text = 'Mz. C lote 12'");
		jdbc.update("update capability set removed_by_user = false");
	}

	@Test
	void inventoryLearnedFromTheSeedAndIsAnonymousForNeighbors() throws Exception {
		Session brigade = TestAuth.login(mvc, "999000111");
		Session maria = TestAuth.login(mvc, "987654321");
		mvc.perform(get("/api/v1/ai/inventory").header("Authorization", brigade.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()", greaterThanOrEqualTo(14)))
				.andExpect(jsonPath("$[*].tag", hasItem("HEIGHT_RESCUE")))
				.andExpect(jsonPath("$[*].tag", hasItem("PARAMEDIC")))
				.andExpect(jsonPath("$[*].tag", hasItem("OXYGEN_DEPENDENT")));
		mvc.perform(get("/api/v1/ai/inventory").param("kind", "VULNERABILITY").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$[*].displayName", everyItem(startsWith("Vecino de "))))
				.andExpect(jsonPath("$[*].evidenceText", everyItem(nullValue())));

		String etag = mvc.perform(get("/api/v1/ai/inventory/snapshot").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(header().exists("ETag"))
				.andReturn().getResponse().getHeader("ETag");
		mvc.perform(get("/api/v1/ai/inventory/snapshot").header("Authorization", maria.bearer())
				.header("If-None-Match", etag)).andExpect(status().isNotModified());
	}

	@Test
	void rescuePlanAssignsTheRightNeighborsAndCreatesPreventiveReports() throws Exception {
		Session brigade = TestAuth.login(mvc, "999000111");
		String plan = mvc.perform(post("/api/v1/ai/rescue-plans").header("Authorization", brigade.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.preventiveReports").value(2))
				.andReturn().getResponse().getContentAsString();

		List<String> teresa = JsonPath.read(plan,
				"$.missions[?(@.reportSummary == 'Adulto mayor solo · Mz. C lote 12')].assignments[*].userName");
		assertThat(teresa).containsExactly("Jorge Paredes", "Pedro Ramos");
		List<String> oxygen = JsonPath.read(plan, "$.missions[?(@.category == 'OXYGEN_DEPENDENT')].assignments[0].userName");
		assertThat(oxygen).containsExactly("Taller Mendoza");
		List<String> preventive = JsonPath.read(plan, "$.missions[?(@.category == 'OXYGEN_DEPENDENT')].reportSummary");
		assertThat(preventive).hasSize(1);

		UUID teresaReport = jdbc.queryForObject("select id from vulnerable_report where address_text = 'Mz. C lote 12'",
				UUID.class);
		mvc.perform(post("/api/v1/ai/missions/" + teresaReport + "/notify").header("Authorization", brigade.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assignedLabel").value("Jorge y Pedro"))
				.andExpect(jsonPath("$.notified").value(2));

		// Un vecino no puede generar planes.
		Session maria = TestAuth.login(mvc, "987654321");
		mvc.perform(post("/api/v1/ai/rescue-plans").header("Authorization", maria.bearer()))
				.andExpect(status().isForbidden());
	}

	@Test
	void deletedLearningIsNeverRecreated() throws Exception {
		Session jorge = TestAuth.login(mvc, "911000009");
		Session admin = TestAuth.login(mvc, "999000000");
		String learnings = mvc.perform(get("/api/v1/me/ai-learnings").header("Authorization", jorge.bearer()))
				.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(learnings, "$[?(@.tag == 'HEIGHT_RESCUE')].id");
		assertThat(ids).hasSize(1);

		mvc.perform(delete("/api/v1/me/ai-learnings/" + ids.getFirst()).header("Authorization", jorge.bearer()))
				.andExpect(status().isNoContent());
		mvc.perform(post("/api/v1/admin/ai/reindex").header("Authorization", admin.bearer()))
				.andExpect(status().isOk());

		String after = mvc.perform(get("/api/v1/me/ai-learnings").header("Authorization", jorge.bearer()))
				.andReturn().getResponse().getContentAsString();
		List<String> stillThere = JsonPath.read(after, "$[?(@.tag == 'HEIGHT_RESCUE')].id");
		assertThat(stillThere).isEmpty();
	}

	@Test
	void inboundSmsCreatesAReport() throws Exception {
		String body = "{\"from\":\"+51987654321\",\"body\":\"NB#SOS!H1@LP340$P:ALTA\"}";
		mvc.perform(post("/api/v1/sms/inbound").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/sms/inbound").header("X-Nearby-Token", "dev-sms-token")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reportCreated").value(true))
				.andExpect(jsonPath("$.decoded.addressText").value("Jr. Los Pinos 340"))
				.andExpect(jsonPath("$.decoded.typeLabel").value("Herido"));
		assertThat(jdbc.queryForObject("select count(*) from vulnerable_report where source = 'SMS'", Integer.class))
				.isEqualTo(1);
	}
}
