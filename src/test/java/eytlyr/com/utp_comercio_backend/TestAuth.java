package eytlyr.com.utp_comercio_backend;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ingreso por OTP para las pruebas de integración (código fijo 123456 solo en las pruebas). */
public final class TestAuth {

	public static final String DEV_CODE = "123456";

	private TestAuth() {
	}

	public record Session(String accessToken, UUID userId) {

		public String bearer() {
			return "Bearer " + accessToken;
		}
	}

	public static Session login(MockMvc mvc, String phone) throws Exception {
		mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\"}")).andExpect(status().isAccepted());
		String body = mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"" + phone + "\",\"code\":\"" + DEV_CODE + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return new Session(JsonPath.read(body, "$.accessToken"), UUID.fromString(JsonPath.read(body, "$.user.id")));
	}
}
