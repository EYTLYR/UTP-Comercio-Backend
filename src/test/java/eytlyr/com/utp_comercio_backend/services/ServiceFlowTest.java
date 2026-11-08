package eytlyr.com.utp_comercio_backend.services;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FASE 5: búsqueda, orden por distancia y las reglas del Visto Bueno Vecinal. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ServiceFlowTest {

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
		jdbc.update("delete from app_user where phone like '9000002%'");
		jdbc.update("delete from zone where name = 'Zona de prueba 5'");
	}

	@Test
	void searchWithoutAccentsAndByStemFindsJulio() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		mvc.perform(get("/api/v1/services").param("q", "gasfitero").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].ownerName").value("Julio Quispe"))
				.andExpect(jsonPath("$.content[0].categoryLabel").value("Gasfitería"));

		// María está en (50,50); Ana Torres (48,46) es la más cercana y Julio la de más vistos buenos.
		mvc.perform(get("/api/v1/services").param("sort", "distance").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$.content[0].ownerName").value("Ana Torres"));
		mvc.perform(get("/api/v1/services").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$.content[0].ownerName").value("Julio Quispe"))
				.andExpect(jsonPath("$.content[0].endorsementCount").value(48));
	}

	@Test
	void endorsementRules() throws Exception {
		UUID gasfiteria = jdbc.queryForObject("select id from neighbor_service where title = 'Gasfitería'", UUID.class);
		String url = "/api/v1/services/" + gasfiteria + "/endorsements";
		Session maria = TestAuth.login(mvc, "987654321");
		Session julio = TestAuth.login(mvc, "911000001");
		Session newcomer = TestAuth.login(mvc, "900000201");
		Session outsider = TestAuth.login(mvc, "900000202");
		jdbc.update("insert into zone (id, name, district, center_lat, center_lng, radius_m) "
				+ "values (?, 'Zona de prueba 5', 'Lima', -12.05, -77.03, 500)", UUID.randomUUID());
		jdbc.update("update app_user set verified = true, zone_id = (select id from zone where name = 'Zona de prueba 5') "
				+ "where id = ?", outsider.userId());

		mvc.perform(post(url).header("Authorization", newcomer.bearer()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(EndorsementService.NOT_VERIFIED))
				.andExpect(jsonPath("$.detail").value("Solo vecinos verificados pueden dar visto bueno"));
		mvc.perform(post(url).header("Authorization", outsider.bearer()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(EndorsementService.OTHER_ZONE));
		mvc.perform(post(url).header("Authorization", julio.bearer()))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value(EndorsementService.SELF));

		mvc.perform(post(url).header("Authorization", maria.bearer()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.endorsementCount").value(49));
		mvc.perform(post(url).header("Authorization", maria.bearer()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(EndorsementService.DUPLICATE));

		String detail = mvc.perform(get("/api/v1/services/" + gasfiteria).header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$.endorsedByMe").value(true))
				.andExpect(jsonPath("$.ownerPhoneMasked").value("911 *** 001"))
				.andExpect(jsonPath("$.latestReviews.length()").value(3))
				.andReturn().getResponse().getContentAsString();
		JsonPath.read(detail, "$.endorserInitials[0]");

		// Se deja el contador como en la semilla (48).
		mvc.perform(delete(url + "/me").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.endorsementCount").value(48));
	}

	@Test
	void serviceIsPublishedWhereTheNeighborIsNow() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		Double homeLat = jdbc.queryForObject("select lat from app_user where phone = '987654321'", Double.class);
		Double homeLng = jdbc.queryForObject("select lng from app_user where phone = '987654321'", Double.class);
		String body = "{\"title\":\"Gasfitero a domicilio\",\"category\":\"PLUMBING\","
				+ "\"availableInEmergency\":true,\"lat\":" + (homeLat + 0.01) + ",\"lng\":" + homeLng + "}";
		String created = mvc.perform(post("/api/v1/services").header("Authorization", maria.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				// ~1,1 km al norte de su casa: la distancia sale de donde publicó, no de la casa.
				.andExpect(jsonPath("$.distanceM", greaterThan(900)))
				.andReturn().getResponse().getContentAsString();
		jdbc.update("delete from neighbor_service where id = ?::uuid", (String) JsonPath.read(created, "$.id"));
	}
}
