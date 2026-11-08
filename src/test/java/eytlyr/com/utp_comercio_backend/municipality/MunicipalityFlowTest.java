package eytlyr.com.utp_comercio_backend.municipality;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una Municipalidad se registra, la plataforma la aprueba y su equipo administra solo su distrito: brigadistas,
 * modo de la zona y equipo. Usuarios 9000005xx.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MunicipalityFlowTest {

	static final String OFFICIAL = "900000501";

	static final String NEIGHBOR = "900000502";

	static final String COLLEAGUE = "900000503";

	static final String OTHER_DISTRICT = "900000504";

	static final String PLATFORM_ADMIN = "900000901";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void cleanOtps() {
		jdbc.update("delete from otp_code");
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from municipality where ruc in ('20131377224', '20131378972')");
		jdbc.update("delete from app_user where phone like '9000005%'");
	}

	@Test
	void municipalityRegistersIsApprovedAndManagesOnlyItsDistrict() throws Exception {
		// 1. Un funcionario registra su Municipalidad (el distrito se compara sin tildes ni mayúsculas).
		Session official = TestAuth.login(mvc, OFFICIAL);
		String created = mvc.perform(post("/api/v1/municipalities").header("Authorization", official.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Municipalidad de San Juan de Lurigancho","district":"san juan de lurigancho",
						 "ruc":"20131377224","contactName":"Ana Torres","contactPosition":"Gerente de Riesgos",
						 "email":"riesgos@munisjl.gob.pe"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(created, "$.id");
		mvc.perform(get("/api/v1/municipalities/mine").header("Authorization", official.bearer()))
				.andExpect(jsonPath("$.status").value("PENDING"));

		// 2. Un vecino no puede aprobarla; el administrador de la plataforma sí.
		Session neighbor = TestAuth.login(mvc, NEIGHBOR);
		mvc.perform(post("/api/v1/admin/municipalities/" + id + "/approve").header("Authorization", neighbor.bearer()))
				.andExpect(status().isForbidden());
		Session admin = TestAuth.login(mvc, PLATFORM_ADMIN);
		mvc.perform(post("/api/v1/admin/municipalities/" + id + "/approve").header("Authorization", admin.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));

		// 3. Al volver a ingresar, el funcionario es MUNICIPALITY y ve las zonas de su distrito.
		official = TestAuth.login(mvc, OFFICIAL);
		mvc.perform(get("/api/v1/me").header("Authorization", official.bearer()))
				.andExpect(jsonPath("$.role").value("MUNICIPALITY"))
				.andExpect(jsonPath("$.municipality.district").value("san juan de lurigancho"));
		String zones = mvc.perform(get("/api/v1/municipality/zones").header("Authorization", official.bearer()))
				.andExpect(jsonPath("$[0].district").value("San Juan de Lurigancho"))
				.andReturn().getResponse().getContentAsString();
		String zoneId = JsonPath.read(zones, "$[0].id");
		mvc.perform(post("/api/v1/admin/zones/" + zoneId + "/mode").header("Authorization", official.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"NORMAL\"}"))
				.andExpect(status().isOk());

		// 4. Un vecino de su distrito pide ser brigadista y la Municipalidad lo aprueba.
		jdbc.update("update app_user set zone_id = ?::uuid where phone = ?", zoneId, NEIGHBOR);
		String request = mvc.perform(post("/api/v1/me/brigade-request").header("Authorization", neighbor.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"skills\":\"Primeros auxilios\"}"))
				.andReturn().getResponse().getContentAsString();
		String requestId = JsonPath.read(request, "$.id");
		mvc.perform(post("/api/v1/admin/brigade-requests/" + requestId + "/approve")
				.header("Authorization", official.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));

		// 5. Arma su equipo con otro funcionario (que ya ingresó a Nearby) y lo puede quitar.
		Session colleague = TestAuth.login(mvc, COLLEAGUE);
		mvc.perform(post("/api/v1/municipality/staff").header("Authorization", official.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+51 900 000 503\"}"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/municipality/staff").header("Authorization", official.bearer()))
				.andExpect(jsonPath("$.length()").value(2));
		mvc.perform(delete("/api/v1/municipality/staff/" + colleague.userId()).header("Authorization", official.bearer()))
				.andExpect(status().isNoContent());

		// 6. Otra Municipalidad (otro distrito) no puede tocar esa zona.
		Session other = TestAuth.login(mvc, OTHER_DISTRICT);
		String otherCreated = mvc.perform(post("/api/v1/municipalities").header("Authorization", other.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Municipalidad de Comas","district":"Comas","ruc":"20131378972",
						 "contactName":"Luis Rojas","email":"riesgos@municomas.gob.pe"}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		mvc.perform(post("/api/v1/admin/municipalities/" + JsonPath.read(otherCreated, "$.id") + "/approve")
				.header("Authorization", admin.bearer()))
				.andExpect(status().isOk());
		other = TestAuth.login(mvc, OTHER_DISTRICT);
		mvc.perform(post("/api/v1/admin/zones/" + zoneId + "/mode").header("Authorization", other.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"EMERGENCY\"}"))
				.andExpect(status().isForbidden());
	}
}
