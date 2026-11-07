package eytlyr.com.utp_comercio_backend.auth;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin NEARBY_OTP_DEV_CODE el código es aleatorio: no se devuelve y 123456 no sirve. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "nearby.otp.dev-fixed-code=")
@AutoConfigureMockMvc
class RandomOtpTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from otp_code");
	}

	@Test
	void codeIsRandomAndNotReturned() throws Exception {
		mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"987654321\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.devCode").doesNotExist());
		mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"987654321\",\"code\":\"123456\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("OTP_INVALID"));
	}
}
