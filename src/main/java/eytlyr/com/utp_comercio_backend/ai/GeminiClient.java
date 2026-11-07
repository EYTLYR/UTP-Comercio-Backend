package eytlyr.com.utp_comercio_backend.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente mínimo de la API REST de Gemini (models/{model}:generateContent) con RestClient.
 * Pide la respuesta en JSON y la devuelve como texto; quien lo usa la interpreta y, si algo falla,
 * cae a su versión de reglas.
 *
 * Circuit breaker simple: tras {@code failureThreshold} fallas seguidas (error, timeout o respuesta vacía)
 * deja de llamar a Gemini durante {@code cooldownSeconds} y responde de inmediato con
 * {@link GeminiUnavailableException}. Pasado ese tiempo prueba una llamada: si sale bien, se cierra.
 * La API key va en el header x-goog-api-key y nunca se escribe en el log.
 */
@Component
@ConditionalOnProperty(prefix = "nearby.ai", name = "provider", havingValue = "gemini")
public class GeminiClient {

	private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Imagen que acompaña al texto (foto del vecino). */
	public record Image(byte[] data, String mimeType) {
	}

	/** Gemini no respondió, respondió mal o el circuito está abierto. */
	public static class GeminiUnavailableException extends RuntimeException {

		GeminiUnavailableException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private final RestClient http;

	private final String model;

	private final String apiKey;

	private final int failureThreshold;

	private final long cooldownNanos;

	private final LongSupplier nanoTime;

	private final AtomicInteger failures = new AtomicInteger();

	private volatile long openUntil;

	@Autowired
	public GeminiClient(NearbyProperties props) {
		this(props.ai().gemini(), System::nanoTime);
	}

	GeminiClient(NearbyProperties.Ai.Gemini cfg, LongSupplier nanoTime) {
		if (cfg == null || cfg.apiKey() == null || cfg.apiKey().isBlank()) {
			throw new IllegalStateException(
					"Gemini: define GEMINI_API_KEY en .env, o usa NEARBY_AI_PROVIDER=rules o anthropic");
		}
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(Math.max(1, cfg.timeoutSeconds())));
		this.http = RestClient.builder()
				.baseUrl(cfg.baseUrl().replaceAll("/+$", ""))
				.requestFactory(factory)
				.build();
		this.model = cfg.model();
		this.apiKey = cfg.apiKey().trim();
		this.failureThreshold = Math.max(1, cfg.failureThreshold());
		this.cooldownNanos = TimeUnit.SECONDS.toNanos(Math.max(1, cfg.cooldownSeconds()));
		this.nanoTime = nanoTime;
		log.info("IA con Gemini ({})", model);
	}

	/** true mientras el circuito está abierto (no se llama a Gemini). */
	public boolean isOpen() {
		return openUntil != 0 && nanoTime.getAsLong() < openUntil;
	}

	/**
	 * Llama a generateContent y devuelve el texto de la respuesta (JSON, por responseMimeType).
	 *
	 * @param image puede ser null (solo texto)
	 * @throws GeminiUnavailableException si el circuito está abierto o la llamada falla
	 */
	public String generateJson(String systemPrompt, String userText, Image image) {
		if (isOpen()) {
			throw new GeminiUnavailableException("Gemini en pausa por fallas seguidas", null);
		}
		try {
			String text = call(systemPrompt, userText, image);
			failures.set(0);
			openUntil = 0;
			return text;
		}
		catch (RuntimeException ex) {
			int n = failures.incrementAndGet();
			if (n >= failureThreshold) {
				openUntil = nanoTime.getAsLong() + cooldownNanos;
				log.warn("Gemini falló {} veces seguidas: se pausa {} s y se usan las reglas.", n,
						TimeUnit.NANOSECONDS.toSeconds(cooldownNanos));
			}
			throw ex instanceof GeminiUnavailableException g ? g
					: new GeminiUnavailableException("Gemini no respondió (" + ex.getClass().getSimpleName() + ")", ex);
		}
	}

	private String call(String systemPrompt, String userText, Image image) {
		List<Map<String, Object>> parts = new ArrayList<>();
		if (image != null) {
			parts.add(Map.of("inlineData", Map.of(
					"mimeType", image.mimeType() != null ? image.mimeType() : "image/jpeg",
					"data", Base64.getEncoder().encodeToString(image.data()))));
		}
		parts.add(Map.of("text", userText));
		Map<String, Object> body = Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
				"contents", List.of(Map.of("role", "user", "parts", parts)),
				"generationConfig", Map.of(
						"responseMimeType", "application/json",
						"temperature", 0.2,
						// Sin "pensar": respuesta corta y rápida (cabe en el timeout).
						"thinkingConfig", Map.of("thinkingBudget", 0)));

		String response = http.post()
				.uri("/v1beta/models/{model}:generateContent", model)
				.header("x-goog-api-key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(JSON.writeValueAsString(body))
				.retrieve()
				.body(String.class);

		JsonNode root = JSON.readTree(response == null ? "{}" : response);
		StringBuilder text = new StringBuilder();
		for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
			text.append(part.path("text").asText(""));
		}
		if (text.isEmpty()) {
			String reason = root.path("candidates").path(0).path("finishReason").asText(
					root.path("promptFeedback").path("blockReason").asText("sin texto"));
			throw new GeminiUnavailableException("Gemini respondió sin texto: " + reason, null);
		}
		return text.toString();
	}
}
