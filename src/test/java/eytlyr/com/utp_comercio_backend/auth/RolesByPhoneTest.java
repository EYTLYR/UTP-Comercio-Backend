package eytlyr.com.utp_comercio_backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import eytlyr.com.utp_comercio_backend.TestAuth;
import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** NEARBY_ADMIN_PHONES / NEARBY_BRIGADE_PHONES asignan el rol al ingresar; el resto queda como vecino. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RolesByPhoneTest {

	@Autowired
	MockMvc mvc;

	@Test
	void rolesComeFromConfiguredPhones() throws Exception {
		expectRole("900000901", "ADMIN");
		expectRole("900000903", "BRIGADIST"); // escrito como "+51 900 000 903" en la lista
		expectRole("900000904", "NEIGHBOR");
	}

	private void expectRole(String phone, String role) throws Exception {
		var session = TestAuth.login(mvc, phone);
		mvc.perform(get("/api/v1/me").header("Authorization", session.bearer()))
				.andExpect(jsonPath("$.role").value(role));
	}
}
