package eytlyr.com.utp_comercio_backend.ai;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.ClassifyResult;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.prevention.GeminiPhotoRiskAnalyzer;
import eytlyr.com.utp_comercio_backend.prevention.PhotoRiskAnalyzer.PhotoAnalysis;
import eytlyr.com.utp_comercio_backend.prevention.RiskCategory;
import eytlyr.com.utp_comercio_backend.prevention.RuleBasedPhotoRiskAnalyzer;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gemini con un servidor falso: foto, micrófono, fallback a reglas y circuit breaker. */
class GeminiTest {

	HttpServer fake;

	final AtomicInteger hits = new AtomicInteger();

	final AtomicReference<String> request = new AtomicReference<>();

	final AtomicReference<String> apiKey = new AtomicReference<>();

	final AtomicReference<String> path = new AtomicReference<>();

	/** JSON que "responde el modelo" (null = error 500). */
	volatile String modelJson;

	final AtomicLong now = new AtomicLong();

	@BeforeEach
	void start() throws Exception {
		fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		fake.createContext("/v1beta/models/", ex -> {
			hits.incrementAndGet();
			path.set(ex.getRequestURI().getPath());
			apiKey.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
			request.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			String json = modelJson;
			byte[] out = (json == null ? "{\"error\":{\"code\":500,\"message\":\"internal\"}}"
					: JsonMapper.builder().build().writeValueAsString(java.util.Map.of("candidates", java.util.List.of(
							java.util.Map.of("content", java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", json))),
									"finishReason", "STOP")))))
					.getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "application/json");
			ex.sendResponseHeaders(json == null ? 500 : 200, out.length);
			ex.getResponseBody().write(out);
			ex.close();
		});
		fake.start();
	}

	@AfterEach
	void stop() {
		fake.stop(0);
	}

	GeminiClient client() {
		var cfg = new NearbyProperties.Ai.Gemini("clave-gemini", "gemini-2.5-flash",
				"http://127.0.0.1:" + fake.getAddress().getPort(), 5, 2, 60);
		return new GeminiClient(cfg, now::get);
	}

	@Test
	void photoIsSentAsBase64AndParsed() {
		modelJson = "{\"category\":\"CRACK\",\"waterLevelCm\":null,\"severity\":0.8,\"summary\":\"Grieta en el muro.\"}";
		var analyzer = new GeminiPhotoRiskAnalyzer(client(), new RuleBasedPhotoRiskAnalyzer());

		PhotoAnalysis a = analyzer.analyze(new byte[] { 1, 2, 3 }, "image/jpeg", null);

		assertThat(a.category()).isEqualTo(RiskCategory.CRACK);
		assertThat(a.severity()).isEqualTo(0.8);
		assertThat(a.summary()).isEqualTo("Grieta en el muro.");
		assertThat(path.get()).isEqualTo("/v1beta/models/gemini-2.5-flash:generateContent");
		assertThat(apiKey.get()).isEqualTo("clave-gemini");
		assertThat(request.get()).contains("\"inlineData\"").contains("\"data\":\"AQID\"") // {1,2,3} en Base64
				.contains("\"responseMimeType\":\"application/json\"");
	}

	@Test
	void photoFallsBackToRulesWhenGeminiFails() {
		modelJson = null; // 500
		var analyzer = new GeminiPhotoRiskAnalyzer(client(), new RuleBasedPhotoRiskAnalyzer());

		PhotoAnalysis a = analyzer.analyze(new byte[] { 1 }, "image/jpeg", RiskCategory.STREET_FLOODING);

		assertThat(a.summary()).isEqualTo(RuleBasedPhotoRiskAnalyzer.summary(RiskCategory.STREET_FLOODING));
	}

	@Test
	void dictatedTextBecomesAForm() {
		modelJson = "{\"type\":\"RESOURCE\",\"kind\":\"OFFER\",\"category\":\"WATER\",\"priority\":\"MEDIUM\",\"location\":null}";
		ClassifyResult r = new GeminiTextClassifier(client()).classify("Tengo agua para compartir en los pinos 340",
				ZoneMode.EMERGENCY);

		assertThat(r.type()).isEqualTo("RESOURCE");
		assertThat(r.kind()).isEqualTo("OFFER");
		assertThat(r.category()).isEqualTo("WATER");
		assertThat(r.location()).isEqualTo("Jr. Los Pinos 340"); // Gemini no la dio: se toma del texto
		assertThat(request.get()).contains("Tengo agua para compartir");
	}

	@Test
	void unknownCategoryFallsBackToRules() {
		modelJson = "{\"type\":\"NEED\",\"kind\":null,\"category\":\"CATEGORIA_INVENTADA\",\"priority\":\"HIGH\"}";
		String text = "Necesito que me presten una escalera";

		ClassifyResult r = new GeminiTextClassifier(client()).classify(text, ZoneMode.NORMAL);

		assertThat(r).isEqualTo(new TextClassifier().classify(text, ZoneMode.NORMAL));
	}

	@Test
	void circuitOpensAfterRepeatedFailuresAndClosesAfterCooldown() {
		modelJson = null;
		GeminiClient gemini = client(); // umbral 2, pausa 60 s
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> gemini.generateJson("s", "t", null))
					.isInstanceOf(GeminiClient.GeminiUnavailableException.class);
		}
		assertThat(gemini.isOpen()).isTrue();

		// Abierto: responde al instante sin llamar a Gemini.
		assertThatThrownBy(() -> gemini.generateJson("s", "t", null))
				.isInstanceOf(GeminiClient.GeminiUnavailableException.class);
		assertThat(hits.get()).isEqualTo(2);

		// Pasada la pausa, prueba de nuevo y, si responde, se cierra.
		now.addAndGet(TimeUnit.SECONDS.toNanos(61));
		modelJson = "{\"ok\":true}";
		assertThat(gemini.generateJson("s", "t", null)).contains("ok");
		assertThat(gemini.isOpen()).isFalse();
		assertThat(hits.get()).isEqualTo(3);
	}
}
