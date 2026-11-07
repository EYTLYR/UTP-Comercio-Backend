package eytlyr.com.utp_comercio_backend.sms;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

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

import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SMS entrante por Twilio: firma X-Twilio-Signature y reporte creado. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TwilioWebhookTest {

	static final String URL = "https://nearby.example/api/v1/sms/inbound/twilio";

	static final Map<String, String> FORM = Map.of("From", "+51987654321", "Body", "NB#SOS!H1@LP340$P:ALTA",
			"To", "+15005550006", "MessageSid", "SM123");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	Timestamp start;

	@BeforeEach
	void setUp() {
		start = Timestamp.from(Instant.now().minusSeconds(1));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from vulnerable_report where source = 'SMS' and created_at >= ?", start);
	}

	@Test
	void signatureMatchesTwilioAlgorithm() {
		// Valor calculado aparte con Python (hmac + sha1 + base64) sobre la misma URL y parámetros.
		assertThat(TwilioWebhookController.sign("test-twilio-token", URL, FORM)).isEqualTo("Rb96lDmxoUI6Lfs6QyrcUw4Zu9M=");
		assertThat(TwilioSmsGateway.toE164("987654321")).isEqualTo("+51987654321");
	}

	@Test
	void signedSmsCreatesReportAndRepliesTwiml() throws Exception {
		var unsigned = post("/api/v1/sms/inbound/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED);
		FORM.forEach(unsigned::param);
		mvc.perform(unsigned.header("X-Twilio-Signature", "firma-falsa")).andExpect(status().isUnauthorized());

		var signed = post("/api/v1/sms/inbound/twilio").contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.header("X-Twilio-Signature", TwilioWebhookController.sign("test-twilio-token", URL, FORM));
		FORM.forEach(signed::param);
		mvc.perform(signed)
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<Message>Nearby recibió tu SOS")));
		assertThat(jdbc.queryForObject(
				"select count(*) from vulnerable_report where source = 'SMS' and created_at >= ?", Integer.class, start))
				.isEqualTo(1);
	}
}
