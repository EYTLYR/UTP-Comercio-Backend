package eytlyr.com.utp_comercio_backend.user;

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
import org.springframework.test.web.servlet.ResultActions;

import eytlyr.com.utp_comercio_backend.TestAuth;
import eytlyr.com.utp_comercio_backend.TestAuth.Session;
import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FASE 4: perfil, ubicación, contactos, ficha médica, IA y checklist. Usa vecinos nuevos (9000001xx). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProfileFlowTest {

	static final String TEST_PHONES = "9000001%";

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
		String ids = "(select id from app_user where phone like '" + TEST_PHONES + "')";
		jdbc.update("delete from capability where user_id in " + ids);
		jdbc.update("delete from sos_alert where user_id in " + ids);
		jdbc.update("delete from app_user where phone like '" + TEST_PHONES + "'");
	}

	// ---------------------------------------------------------------- nombre y ubicación

	@Test
	void updateName() throws Exception {
		Session s = TestAuth.login(mvc, "900000101");
		putJson(s, "/api/v1/me", "{\"name\":\"  Rosa Huamán  \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Rosa Huamán"));
		putJson(s, "/api/v1/me", "{\"name\":\"R\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("El nombre debe tener entre 2 y 60 caracteres"));
	}

	@Test
	void locationAssignsZona3AndNeverReturnsTheAddress() throws Exception {
		Session s = TestAuth.login(mvc, "900000102");
		mvc.perform(get("/api/v1/me").header("Authorization", s.bearer()))
				.andExpect(jsonPath("$.zone").doesNotExist());

		putJson(s, "/api/v1/me/location", "{\"lat\":-11.9781,\"lng\":-77.0042,\"addressText\":\"Jr. Los Pinos 340\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.zone.name").value("Zona 3 · Las Flores"))
				.andExpect(jsonPath("$.effectiveMode").value("NORMAL"))
				.andExpect(jsonPath("$.addressText").doesNotExist());
		assertThat(jdbc.queryForObject("select address_text from app_user where id = ?", String.class, s.userId()))
				.isEqualTo("Jr. Los Pinos 340");

		mvc.perform(get("/api/v1/me/location").header("Authorization", s.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.addressText").value("Jr. Los Pinos 340"))
				.andExpect(jsonPath("$.lat").value(-11.9781))
				.andExpect(jsonPath("$.lng").value(-77.0042))
				.andExpect(jsonPath("$.zoneName").value("Zona 3 · Las Flores"));

		// Editar solo la dirección manteniendo las coordenadas existentes
		putJson(s, "/api/v1/me/location", "{\"addressText\":\"Av. Las Flores 500, Dpto 302\"}")
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/me/location").header("Authorization", s.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.addressText").value("Av. Las Flores 500, Dpto 302"))
				.andExpect(jsonPath("$.lat").value(-11.9781));
	}

	@Test
	void locationOutsideCoverageUsesNearestPilotZoneAndPreservesCoordinates() throws Exception {
        Session s = TestAuth.login(mvc, "900000103");
        putJson(s, "/api/v1/me/location", "{\"lat\":-13.5320,\"lng\":-71.9675,\"addressText\":\"Dirección privada en Cusco\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone.name").value("Zona 3 · Las Flores"))
                .andExpect(jsonPath("$.addressText").doesNotExist());
        assertThat(jdbc.queryForObject("select lat from app_user where id = ?", Double.class, s.userId()))
                .isEqualTo(-13.5320);
        assertThat(jdbc.queryForObject("select lng from app_user where id = ?", Double.class, s.userId()))
                .isEqualTo(-71.9675);
        assertThat(jdbc.queryForObject("select address_text from app_user where id = ?", String.class, s.userId()))
                .isEqualTo("Dirección privada en Cusco");
    }

	// ---------------------------------------------------------------- contactos de confianza

	@Test
	void trustedContactsUpToThree() throws Exception {
		Session s = TestAuth.login(mvc, "900000104");
		putJson(s, "/api/v1/me/trusted-contacts", """
				[{"name":"Mamá","phone":"987000120"},{"name":"Carlos","phone":"956000447"},
				 {"name":"Sra. Rosa","phone":"944000908"}]""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[2].position").value(3));
		mvc.perform(get("/api/v1/me/trusted-contacts").header("Authorization", s.bearer()))
				.andExpect(jsonPath("$[0].name").value("Mamá"));

		putJson(s, "/api/v1/me/trusted-contacts", """
				[{"name":"A","phone":"911111111"},{"name":"B","phone":"922222222"},
				 {"name":"C","phone":"933333333"},{"name":"D","phone":"944444444"}]""")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value(ProfileService.TRUSTED_CONTACTS_MAX));

		// La lista anterior sigue intacta.
		mvc.perform(get("/api/v1/me/trusted-contacts").header("Authorization", s.bearer()))
				.andExpect(jsonPath("$", hasSize(3)));
	}

	@Test
	void trustedContactValidation() throws Exception {
		Session s = TestAuth.login(mvc, "900000105");
		putJson(s, "/api/v1/me/trusted-contacts", "[{\"name\":\"Mamá\",\"phone\":\"123\"}]")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		putJson(s, "/api/v1/me/trusted-contacts", """
				[{"name":"A","phone":"911111111","position":1},{"name":"B","phone":"922222222","position":1}]""")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value(ProfileService.TRUSTED_CONTACTS_POSITION));
	}

	// ---------------------------------------------------------------- ficha médica

	@Test
	void ownMedicalCard() throws Exception {
		Session s = TestAuth.login(mvc, "900000106");
		mvc.perform(get("/api/v1/me/medical-card").header("Authorization", s.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.livesWith", hasSize(0)));
		putJson(s, "/api/v1/me/medical-card",
				"{\"bloodType\":\"O+\",\"allergies\":\"Penicilina\",\"livesWith\":[\"ELDERLY\",\"PETS\",\"ELDERLY\"]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bloodType").value("O+"))
				.andExpect(jsonPath("$.livesWith", hasSize(2)));
		mvc.perform(get("/api/v1/me/medical-card").header("Authorization", s.bearer()))
				.andExpect(jsonPath("$.allergies").value("Penicilina"))
				.andExpect(jsonPath("$.livesWith[1]").value("PETS"));
		putJson(s, "/api/v1/me/medical-card", "{\"bloodType\":\"Z9\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void brigadeSeesTheMedicalCardOnlyDuringAnActiveSos() throws Exception {
		Session person = TestAuth.login(mvc, "900000107");
		putJson(person, "/api/v1/me/medical-card", "{\"bloodType\":\"B-\",\"conditions\":\"Diabetes\"}")
				.andExpect(status().isOk());
		Session neighbor = TestAuth.login(mvc, "900000108");
		Session brigade = TestAuth.login(mvc, "999000111");
		String url = "/api/v1/brigade/medical-cards/" + person.userId();

		mvc.perform(get(url).header("Authorization", neighbor.bearer()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(get(url).header("Authorization", brigade.bearer()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ProfileService.MEDICAL_CARD_NOT_AVAILABLE));

		jdbc.update("insert into sos_alert (id, user_id, zone_id, lat, lng, status) "
				+ "values (?, ?, (select id from zone limit 1), -11.979, -77.003, 'ACTIVE')", UUID.randomUUID(),
				person.userId());
		mvc.perform(get(url).header("Authorization", brigade.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.medicalCard.bloodType").value("B-"))
				.andExpect(jsonPath("$.medicalCard.conditions").value("Diabetes"));
	}

	// ---------------------------------------------------------------- IA y ajustes

	@Test
	void deleteAnAiLearning() throws Exception {
		Session s = TestAuth.login(mvc, "900000109");
		Session other = TestAuth.login(mvc, "900000110");
		UUID capability = insertCapability(s.userId(), "HEIGHT_RESCUE", "Presto escalera grande de 6 metros");

		mvc.perform(get("/api/v1/me/ai-learnings").header("Authorization", s.bearer()))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].tag").value("HEIGHT_RESCUE"))
				.andExpect(jsonPath("$[0].label").value("Rescate en altura"))
				.andExpect(jsonPath("$[0].sourceType").value("POST"));

		mvc.perform(delete("/api/v1/me/ai-learnings/" + capability).header("Authorization", other.bearer()))
				.andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/me/ai-learnings/" + capability).header("Authorization", s.bearer()))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/me/ai-learnings").header("Authorization", s.bearer()))
				.andExpect(jsonPath("$", hasSize(0)));
		assertThat(jdbc.queryForObject("select removed_by_user from capability where id = ?", Boolean.class, capability))
				.isTrue();
	}

	@Test
	void withdrawingAiConsentRemovesEverythingLearned() throws Exception {
		Session s = TestAuth.login(mvc, "900000111");
		insertCapability(s.userId(), "BACKUP_POWER", "Presto mi generador");
		insertCapability(s.userId(), "PUMP", "Tengo una motobomba");

		putJson(s, "/api/v1/me/settings", "{\"aiConsent\":false}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aiConsent").value(false));
		mvc.perform(get("/api/v1/me/ai-learnings").header("Authorization", s.bearer()))
				.andExpect(jsonPath("$", hasSize(0)));
	}

	// ---------------------------------------------------------------- checklist y dispositivo

	@Test
	void prepChecklist() throws Exception {
		Session s = TestAuth.login(mvc, "900000112");
		mvc.perform(get("/api/v1/me/prep-checklist").header("Authorization", s.bearer()))
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].itemKey").value("GUTTERS"))
				.andExpect(jsonPath("$[0].done").value(false));
		putJson(s, "/api/v1/me/prep-checklist", "[{\"itemKey\":\"GO_BAG\",\"done\":true}]")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[2].itemKey").value("GO_BAG"))
				.andExpect(jsonPath("$[2].done").value(true))
				.andExpect(jsonPath("$[0].done").value(false));
		putJson(s, "/api/v1/me/prep-checklist", "[{\"itemKey\":\"GO_BAG\",\"done\":false}]")
				.andExpect(jsonPath("$[2].done").value(false));
	}

	@Test
	void deviceTokenMovesToTheLatestUser() throws Exception {
		Session a = TestAuth.login(mvc, "900000113");
		Session b = TestAuth.login(mvc, "900000114");
		String body = "{\"token\":\"fcm-demo-token-1\",\"platform\":\"ANDROID\"}";
		putJson(a, "/api/v1/me/device-token", body).andExpect(status().isNoContent());
		putJson(b, "/api/v1/me/device-token", body).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select user_id from device_token where token = 'fcm-demo-token-1'", UUID.class))
				.isEqualTo(b.userId());
	}

	// ---------------------------------------------------------------- utilidades

	ResultActions putJson(Session s, String url, String json) throws Exception {
		return mvc.perform(put(url).header("Authorization", s.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	UUID insertCapability(UUID userId, String tag, String evidence) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into capability (id, zone_id, user_id, display_name, tag, kind, source_type, source_id, "
				+ "evidence_text, confidence, lat, lng) values (?, (select id from zone limit 1), ?, 'Vecino', ?, "
				+ "'RESOURCE', 'POST', ?, ?, 0.9, -11.979, -77.003)", id, userId, tag, UUID.randomUUID(), evidence);
		return id;
	}
}
