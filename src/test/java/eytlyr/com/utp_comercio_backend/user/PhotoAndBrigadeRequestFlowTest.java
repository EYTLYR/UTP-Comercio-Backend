package eytlyr.com.utp_comercio_backend.user;

import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import eytlyr.com.utp_comercio_backend.TestAuth;
import eytlyr.com.utp_comercio_backend.TestAuth.Session;
import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Foto de perfil y solicitud de brigadista aprobada por la Municipalidad. Vecinos 9000004xx. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PhotoAndBrigadeRequestFlowTest {

	static final String NEIGHBOR = "900000401";

	static final String ADMIN = "900000901";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void cleanOtps() {
		jdbc.update("delete from otp_code");
	}

	@AfterEach
	void removeTestUsers() {
		jdbc.update("delete from app_user where phone like '9000004%'");
	}

	@Test
	void neighborUploadsPhotoAsksToJoinTheBrigadeAndTheMunicipalityApproves() throws Exception {
		Session vecino = TestAuth.login(mvc, NEIGHBOR);

		MockMultipartFile photo = new MockMultipartFile("photo", "yo.png", "image/png",
				new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A });
		mvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo").file(photo).header("Authorization", vecino.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.photoUrl").value(startsWith("/files/")));

		mvc.perform(post("/api/v1/me/brigade-request").header("Authorization", vecino.bearer())
						.contentType(MediaType.APPLICATION_JSON).content("{\"skills\":\"Primeros auxilios\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));

		// Un vecino no ve el panel de la Municipalidad.
		mvc.perform(get("/api/v1/admin/brigade-requests").header("Authorization", vecino.bearer()))
				.andExpect(status().isForbidden());

		Session admin = TestAuth.login(mvc, ADMIN);
		String pending = mvc.perform(get("/api/v1/admin/brigade-requests").header("Authorization", admin.bearer()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(pending, "$[?(@.phone == '" + NEIGHBOR + "')].id");
		assertThat(ids).hasSize(1);

		mvc.perform(post("/api/v1/admin/brigade-requests/" + ids.get(0) + "/approve")
						.header("Authorization", admin.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));

		mvc.perform(get("/api/v1/me").header("Authorization", vecino.bearer()))
				.andExpect(jsonPath("$.role").value("BRIGADIST"))
				.andExpect(jsonPath("$.photoUrl").value(startsWith("/files/")));
		mvc.perform(get("/api/v1/me/brigade-request").header("Authorization", vecino.bearer()))
				.andExpect(jsonPath("$.status").value("APPROVED"));
	}
}
