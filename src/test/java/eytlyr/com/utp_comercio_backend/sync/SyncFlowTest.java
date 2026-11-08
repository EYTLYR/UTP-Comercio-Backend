package eytlyr.com.utp_comercio_backend.sync;

import java.sql.Timestamp;
import java.time.Instant;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FASE 11: paquete de sincronización y cola del teléfono (creado, duplicado y error en el mismo envío). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SyncFlowTest {

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
	void cleanUp() {
		jdbc.update("delete from post where created_at >= ?", start);
		jdbc.update("delete from check_in where created_at >= ?", start);
	}

	@Test
	void fullSyncAndIncrementalSync() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		mvc.perform(get("/api/v1/sync").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(header().exists("X-Correlation-Id"))
				.andExpect(jsonPath("$.full").value(true))
				.andExpect(jsonPath("$.zone.name").value("Zona 3 · Las Flores"))
				.andExpect(jsonPath("$.services.length()").value(7))
				.andExpect(jsonPath("$.streetCodes.length()").value(5))
				.andExpect(jsonPath("$.inventorySnapshot.items.length()", greaterThanOrEqualTo(14)))
				.andExpect(jsonPath("$.riskZones.length()").value(2))
				.andExpect(jsonPath("$.vulnerableReports[0].exactLocation").value(false));
		mvc.perform(get("/api/v1/sync").param("since", "2099-01-01T00:00:00Z").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$.full").value(false))
				.andExpect(jsonPath("$.services.length()").value(0))
				.andExpect(jsonPath("$.posts.length()").value(0));
	}

	@Test
	void outboxProcessesInOrderWithDuplicatesAndErrors() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		String clientId = UUID.randomUUID().toString();
		String post = "{\"kind\":\"POST\",\"payload\":{\"clientId\":\"" + clientId
				+ "\",\"type\":\"OFFER\",\"category\":\"TOOL_LOAN\",\"text\":\"Presto mi carretilla\"}}";
		long pastExpiresAt = System.currentTimeMillis() - 10000;
		String expiredId = UUID.randomUUID().toString();
		String expiredPost = "{\"kind\":\"POST\",\"payload\":{\"clientId\":\"" + expiredId
				+ "\",\"type\":\"NEED\",\"category\":\"TEMPORARY_HELP\",\"text\":\"Empujar auto caducado\",\"expiresAt\":" + pastExpiresAt + "}}";
		String body = "[" + post + "," + post
				+ ",{\"kind\":\"VULNERABLE_REPORT\",\"payload\":{\"clientId\":\"" + UUID.randomUUID() + "\"}}"
				+ ",{\"kind\":\"CHECK_IN\",\"payload\":{\"status\":\"SAFE\"}},"
				+ expiredPost + "]";
		mvc.perform(post("/api/v1/sync/outbox").header("Authorization", maria.bearer())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$[0].status").value("CREATED"))
				.andExpect(jsonPath("$[0].clientId").value(clientId))
				.andExpect(jsonPath("$[1].status").value("DUPLICATE"))
				.andExpect(jsonPath("$[2].status").value("ERROR"))
				.andExpect(jsonPath("$[2].code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$[2].error").value("Elige la categoría"))
				.andExpect(jsonPath("$[3].status").value("CREATED"))
				.andExpect(jsonPath("$[4].status").value("ERROR"))
				.andExpect(jsonPath("$[4].code").value("EXPIRED"));
	}
}
