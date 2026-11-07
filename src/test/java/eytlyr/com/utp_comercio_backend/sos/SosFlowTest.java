package eytlyr.com.utp_comercio_backend.sos;

import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;

import eytlyr.com.utp_comercio_backend.TestAuth;
import eytlyr.com.utp_comercio_backend.TestAuth.Session;
import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;
import eytlyr.com.utp_comercio_backend.seed.DemoDataSeeder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FASE 7: SOS idempotente con avisos, respuesta de un vecino, activación automática y modos. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SosFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Timestamp start;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from otp_code");
		start = Timestamp.from(Instant.now().minusSeconds(1));
	}

	@AfterEach
	void restoreZone() {
		jdbc.update("delete from sos_response where created_at >= ?", start);
		jdbc.update("delete from sos_alert where created_at >= ?", start);
		jdbc.update("update zone set mode = 'NORMAL', mode_source = null, mode_reason = null, mode_activated_at = null");
		jdbc.update("update app_user set mode_dismissed_at = null, local_emergency = false");
	}

	@Test
	void sosIsIdempotentNotifiesAndCanBeClosed() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		Session elena = TestAuth.login(mvc, "911000008");
		String body = "{\"clientId\":\"" + UUID.randomUUID() + "\",\"lat\":" + DemoDataSeeder.lat(50) + ",\"lng\":"
				+ DemoDataSeeder.lng(50) + "}";

		String created = sos(maria, body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.contactsNotified").value(3))
				.andExpect(jsonPath("$.brigadeNotified").value(1))
				.andExpect(jsonPath("$.smsCode").value("NB#SOS!X1@LF0$P:ALTA"))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");
		int neighbors = JsonPath.read(created, "$.notifiedNeighbors");
		org.assertj.core.api.Assertions.assertThat(neighbors).isGreaterThan(3);

		sos(maria, body).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

		mvc.perform(post("/api/v1/sos/" + id + "/responses").header("Authorization", elena.bearer())
						.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"GOING\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responders[0].name").value("Elena Vargas"))
				.andExpect(jsonPath("$.responders[0].etaMinutes").value(2)); // unos 86 m a 80 m/min

		mvc.perform(get("/api/v1/sos/nearby").header("Authorization", elena.bearer()))
				.andExpect(jsonPath("$[0].id").value(id))
				.andExpect(jsonPath("$[0].goingCount").value(1));

		mvc.perform(post("/api/v1/sos/" + id + "/safe").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SAFE"));
	}

	@Test
	void fiveSosActivateEmergencyDismissAndReactivation() throws Exception {
		String[] phones = { "911000001", "911000002", "911000003", "911000004", "911000005" };
		for (int i = 0; i < phones.length; i++) {
			Session s = TestAuth.login(mvc, phones[i]);
			sos(s, "{\"lat\":-11.979,\"lng\":-77.003,\"type\":\"EARTHQUAKE\"}")
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.zoneEmergencyActivated").value(i == phones.length - 1));
		}

		Session maria = TestAuth.login(mvc, "987654321");
		mvc.perform(get("/api/v1/zones/current").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$.mode").value("EMERGENCY"))
				.andExpect(jsonPath("$.modeSource").value("AUTO_SOS"));
		mvc.perform(get("/api/v1/me").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$.effectiveMode").value("EMERGENCY"));

		mode(maria, "DISMISS").andExpect(jsonPath("$.effectiveMode").value("NORMAL"));

		// La Municipalidad vuelve a activar el modo: María lo vuelve a ver.
		Session admin = TestAuth.login(mvc, "999000000");
		String zoneId = jdbc.queryForObject("select id::text from zone where name = ?", String.class,
				DemoDataSeeder.ZONE_NAME);
		Thread.sleep(5);
		mvc.perform(post("/api/v1/admin/zones/" + zoneId + "/mode").header("Authorization", admin.bearer())
						.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EMERGENCY\",\"reason\":\"Sismo 6.8\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modeSource").value("MUNICIPALITY"));
		mvc.perform(get("/api/v1/me").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$.effectiveMode").value("EMERGENCY"));

		// Un vecino no puede cambiar el modo de la zona.
		mvc.perform(post("/api/v1/admin/zones/" + zoneId + "/mode").header("Authorization", maria.bearer())
						.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"NORMAL\"}"))
				.andExpect(status().isForbidden());
	}

	ResultActions sos(Session s, String body) throws Exception {
		return mvc.perform(post("/api/v1/sos").header("Authorization", s.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	ResultActions mode(Session s, String action) throws Exception {
		return mvc.perform(post("/api/v1/me/mode").header("Authorization", s.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}"));
	}
}
