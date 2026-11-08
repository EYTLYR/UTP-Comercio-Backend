package eytlyr.com.utp_comercio_backend.posts;

import java.sql.Timestamp;
import java.time.Instant;
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
import eytlyr.com.utp_comercio_backend.chat.ChatService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FASE 6: idempotencia del muro, "Yo te ayudo" abre el chat y solo los participantes lo leen. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PostChatFlowTest {

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
		jdbc.update("delete from conversation_message where created_at >= ?", start);
		jdbc.update("delete from conversation where created_at >= ?", start);
		jdbc.update("delete from post_response where created_at >= ?", start);
		jdbc.update("delete from post where created_at >= ?", start);
	}

	@Test
	void sameClientIdReturnsTheSamePost() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		String body = "{\"clientId\":\"" + UUID.randomUUID() + "\",\"type\":\"NEED\",\"category\":\"TOOL_LOAN\"}";

		String first = mvc.perform(post("/api/v1/posts").header("Authorization", maria.bearer())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.text").value("Necesito: préstamo de herramientas"))
				.andReturn().getResponse().getContentAsString();
		mvc.perform(post("/api/v1/posts").header("Authorization", maria.bearer())
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")));
	}

	@Test
	void respondingOpensTheChatAndOnlyParticipantsCanReadIt() throws Exception {
		Session maria = TestAuth.login(mvc, "987654321");
		Session elena = TestAuth.login(mvc, "911000008");
		Session jorge = TestAuth.login(mvc, "911000009");

		String post = mvc.perform(post("/api/v1/posts").header("Authorization", maria.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"NEED\",\"category\":\"TEMPORARY_HELP\",\"text\":\"Ayuda para cargar un ropero\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String postId = JsonPath.read(post, "$.id");

		String resp = mvc.perform(post("/api/v1/posts/" + postId + "/responses").header("Authorization", elena.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		String responseId = JsonPath.read(resp, "$.id");

		String ref = mvc.perform(post("/api/v1/posts/" + postId + "/responses/" + responseId + "/accept").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String conversationId = JsonPath.read(ref, "$.conversationId");

		// Elena escribe dos veces con el mismo clientId: un solo mensaje.
		String msg = "{\"clientId\":\"" + UUID.randomUUID() + "\",\"text\":\"Voy a las 5 pm\"}";
		mvc.perform(post("/api/v1/conversations/" + conversationId + "/messages").header("Authorization", elena.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(msg)).andExpect(status().isCreated());
		mvc.perform(post("/api/v1/conversations/" + conversationId + "/messages").header("Authorization", elena.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(msg)).andExpect(status().isOk());

		mvc.perform(get("/api/v1/conversations").header("Authorization", maria.bearer()))
				.andExpect(jsonPath("$[0].id").value(conversationId))
				.andExpect(jsonPath("$[0].otherUserName").value("Elena Vargas"))
				.andExpect(jsonPath("$[0].unread").value(2));

		mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages").header("Authorization", maria.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].system").value(true))
				.andExpect(jsonPath("$[0].text").value("Aceptaste la ayuda de Elena Vargas para: «Ayuda para cargar un ropero»"))
				.andExpect(jsonPath("$[1].senderName").value("Elena Vargas"));

		mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages").header("Authorization", jorge.bearer()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ChatService.CHAT_FORBIDDEN));

		mvc.perform(get("/api/v1/posts/" + postId).header("Authorization", elena.bearer()))
				.andExpect(jsonPath("$.respondedByMe").value(true));
		assertThat(jdbc.queryForObject("select count(*) from post_response where post_id = ?::uuid", Integer.class,
				postId)).isEqualTo(1);
	}
}
