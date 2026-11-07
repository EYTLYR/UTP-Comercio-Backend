package eytlyr.com.utp_comercio_backend.prevention;

import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import eytlyr.com.utp_comercio_backend.TestAuth;
import eytlyr.com.utp_comercio_backend.TestAuth.Session;
import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;
import eytlyr.com.utp_comercio_backend.seed.DemoDataSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FASE 10: radar con los datos semilla, el tercer reporte que sube Jr. Los Pinos a RED, mitigación y coordinación. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PreventionFlowTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RiskClusteringService radar;

	Timestamp start;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from otp_code");
		start = Timestamp.from(Instant.now().minusSeconds(1));
	}

	@AfterEach
	void restore() {
		jdbc.update("delete from conversation_message where created_at >= ?", start);
		jdbc.update("delete from conversation where created_at >= ?", start);
		jdbc.update("delete from risk_report where created_at >= ?", start);
		jdbc.update("update mitigation_match set coordinated_at = null");
		radar.recompute(jdbc.queryForObject("select id from zone where name = ?", UUID.class, DemoDataSeeder.ZONE_NAME));
	}

	@Test
	void seedRadarHeatMapAndMitigation() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		String body = mvc.perform(post("/api/v1/risk-zones/analyze").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.zones.length()").value(2))
				.andReturn().getResponse().getContentAsString();

		assertThat((List<String>) JsonPath.read(body, "$.zones[?(@.name == 'Quebrada Canto Grande')].level"))
				.containsExactly("RED");
		assertThat((List<Integer>) JsonPath.read(body, "$.zones[?(@.name == 'Quebrada Canto Grande')].reportCount"))
				.containsExactly(3);
		assertThat((List<String>) JsonPath.read(body, "$.zones[?(@.name == 'Jr. Los Pinos')].level")).containsExactly("AMBER");
		assertThat((List<String>) JsonPath.read(body, "$.zones[?(@.name == 'Jr. Los Pinos')].pattern"))
				.containsExactly("2 vecinos de Jr. Los Pinos reportaron filtración y acequia al ras en la última hora: "
						+ "la acequia podría desbordarse y anegar la calle.");

		List<String> helper = JsonPath.read(body, "$.zones[?(@.name == 'Jr. Los Pinos')].mitigation[0].userName");
		assertThat(helper).containsExactly("Familia Ríos");
		List<List<String>> labels = JsonPath.read(body, "$.zones[?(@.name == 'Jr. Los Pinos')].mitigation[0].labels");
		assertThat(labels.getFirst()).contains("Sacos de arena", "Motobomba");
		List<Integer> distance = JsonPath.read(body, "$.zones[?(@.name == 'Jr. Los Pinos')].mitigation[0].distanceM");
		assertThat(distance.getFirst()).isBetween(120, 200);

		mvc.perform(get("/api/v1/map/heat").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].level").value("RED"))
				.andExpect(jsonPath("$[0].radiusM").value(220))
				.andExpect(jsonPath("$[0].label").value("Riesgo inminente · Quebrada Canto Grande"));

		mvc.perform(get("/api/v1/mitigation/summary").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$[0].type").value("SANDBAGS"))
				.andExpect(jsonPath("$[0].have").value(40))
				.andExpect(jsonPath("$[0].need").value(120));
	}

	@Test
	void thirdReportWithPhotoEscalatesToRedAndCoordinationOpensTheChat() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		MockMultipartFile data = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
				("{\"clientId\":\"" + UUID.randomUUID() + "\",\"category\":\"SEEPAGE\",\"text\":\"Está brotando agua "
						+ "por la pared de la sala\",\"addressText\":\"Jr. Los Pinos 330\"}").getBytes());
		MockMultipartFile photo = new MockMultipartFile("photo", "pared.jpg", MediaType.IMAGE_JPEG_VALUE,
				new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02 });

		String created = mvc.perform(multipart("/api/v1/risk-reports").file(data).file(photo)
						.header("Authorization", maria.bearer()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.escalated").value(true))
				.andExpect(jsonPath("$.riskZone.name").value("Jr. Los Pinos"))
				.andExpect(jsonPath("$.riskZone.level").value("RED"))
				.andExpect(jsonPath("$.riskZone.reportCount").value(3))
				.andExpect(jsonPath("$.riskZone.alertText", containsString("Familia Ríos tiene sacos de arena y motobomba")))
				.andExpect(jsonPath("$.photoAnalysis.summary")
						.value("Mancha de humedad y agua brotando del piso. Posible filtración subterránea."))
				.andExpect(jsonPath("$.report.photoUrl", containsString("/files/")))
				.andReturn().getResponse().getContentAsString();

		String photoUrl = JsonPath.read(created, "$.report.photoUrl");
		mvc.perform(get(photoUrl)).andExpect(status().isOk());

		String zoneId = JsonPath.read(created, "$.riskZone.id");
		mvc.perform(post("/api/v1/risk-zones/" + zoneId + "/coordinate").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.conversationId").exists());
		mvc.perform(get("/api/v1/risk-zones").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$[?(@.name == 'Jr. Los Pinos')].mitigation[0].coordinated").value(true));
		mvc.perform(get("/api/v1/conversations").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$[0].otherUserName").value("Familia Ríos"))
				.andExpect(jsonPath("$[0].lastMessage", containsString("Préstamo preventivo para Jr. Los Pinos")));
	}
}
