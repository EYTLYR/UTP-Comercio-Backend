package eytlyr.com.utp_comercio_backend.posts;

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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un vecino registra una zona nueva y su vecina de la zona de al lado ve lo que él publica, en el muro y en el
 * mapa con un texto que dice quién pide qué. Vecinos 9000007xx.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NearbyAcrossZonesFlowTest {

	static final String AUTHOR = "900000701";

	static final String NEIGHBOR = "900000702";

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
		String ids = "(select id from app_user where phone like '9000007%')";
		String convs = "(select id from conversation where post_id in (select id from post where author_id in " + ids
				+ "))";
		jdbc.update("delete from conversation_message where conversation_id in " + convs);
		jdbc.update("delete from conversation where id in " + convs);
		jdbc.update("delete from post_response where post_id in (select id from post where author_id in " + ids + ")");
		jdbc.update("delete from capability where user_id in " + ids);
		jdbc.update("delete from post where author_id in " + ids);
		jdbc.update("delete from app_user where phone like '9000007%'");
		jdbc.update("delete from zone where name like 'Urb. Prueba Siete%'");
	}

	@Test
	void neighborsInAdjacentZonesSeeEachOther() throws Exception {
		// 1. El autor registra su zona y queda en ella; su vecina vive a ~300 m, pero en la zona de ejemplo.
		Session author = TestAuth.login(mvc, AUTHOR);
		String zone = mvc.perform(post("/api/v1/zones").header("Authorization", author.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Urb. Prueba Siete\",\"lat\":-11.9760,\"lng\":-77.0030,\"radiusM\":400,"
						+ "\"district\":\"San Juan de Lurigancho\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String zoneId = JsonPath.read(zone, "$.id");
		jdbc.update("update app_user set zone_id = ?::uuid, lat = -11.9760, lng = -77.0030 where phone = ?", zoneId,
				AUTHOR);
		Session neighbor = TestAuth.login(mvc, NEIGHBOR);
		jdbc.update("update app_user set zone_id = (select id from zone where name = 'Zona 3 · Las Flores'), "
				+ "lat = -11.9787, lng = -77.0030 where phone = ?", NEIGHBOR);

		// 2. Pide ayuda; ella lo ve en su muro y en su mapa aunque estén en zonas distintas.
		mvc.perform(post("/api/v1/posts").header("Authorization", author.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"NEED\",\"category\":\"TEMPORARY_HELP\",\"text\":\"Necesito ayuda para cargar\"}"))
				.andExpect(status().isCreated());
		mvc.perform(get("/api/v1/posts").header("Authorization", neighbor.bearer()))
				.andExpect(jsonPath("$.content[*].text", hasItem("Necesito ayuda para cargar")));
		mvc.perform(get("/api/v1/map/pins").header("Authorization", neighbor.bearer()))
				.andExpect(jsonPath("$[*].title", hasItem(org.hamcrest.Matchers.endsWith("necesita ayuda"))));

		// 3. Ella responde; él ve en su publicación cuántos vecinos respondieron.
		String list = mvc.perform(get("/api/v1/posts").header("Authorization", neighbor.bearer()))
				.andReturn().getResponse().getContentAsString();
		String postId = JsonPath.<java.util.List<String>>read(list,
				"$.content[?(@.text == 'Necesito ayuda para cargar')].id").get(0);
		mvc.perform(post("/api/v1/posts/" + postId + "/responses").header("Authorization", neighbor.bearer()))
				.andExpect(status().is2xxSuccessful());
		mvc.perform(get("/api/v1/posts").header("Authorization", author.bearer()))
				.andExpect(jsonPath("$.content[?(@.id == '" + postId + "')].mine", hasItem(true)))
				.andExpect(jsonPath("$.content[?(@.id == '" + postId + "')].responsesCount", hasItem(1)));
	}
}
