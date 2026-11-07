package eytlyr.com.utp_comercio_backend.prevention;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.config.NearbyProperties;

/**
 * Análisis de la foto con un modelo con visión (nearby.ai.provider=anthropic). Pide solo JSON y, si falla
 * o tarda más de 8 s, usa el analizador de reglas. La API key nunca se escribe en el log.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "nearby.ai", name = "provider", havingValue = "anthropic")
public class AnthropicPhotoRiskAnalyzer implements PhotoRiskAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(AnthropicPhotoRiskAnalyzer.class);

	private static final ObjectMapper JSON = new ObjectMapper();

	static final String SYSTEM_PROMPT = """
			Eres el analista de riesgos de lluvias (Fenómeno El Niño) de una app vecinal de Lima.
			Mira la foto que envió un vecino y clasifica el riesgo con una de estas categorías:
			%s
			Responde SOLO con JSON, sin texto adicional:
			{"category":"<CATEGORIA>","waterLevelCm":<entero o null>,"severity":<0 a 1>,"summary":"<1 frase en español>"}
			""".formatted(Arrays.stream(RiskCategory.values())
			.map(c -> "- " + c.name() + ": " + c.label())
			.collect(Collectors.joining("\n")));

	private final AnthropicClient client;

	private final String model;

	private final PhotoRiskAnalyzer fallback;

	public AnthropicPhotoRiskAnalyzer(NearbyProperties props,
			@Qualifier("ruleBasedPhotoRiskAnalyzer") PhotoRiskAnalyzer fallback) {
		this.client = AnthropicOkHttpClient.builder()
				.apiKey(props.ai().apiKey())
				.timeout(Duration.ofSeconds(8))
				.maxRetries(0)
				.build();
		this.model = props.ai().model();
		this.fallback = fallback;
	}

	@Override
	public PhotoAnalysis analyze(byte[] image, String mediaType, RiskCategory hint) {
		try {
			Base64ImageSource source = Base64ImageSource.builder()
					.data(Base64.getEncoder().encodeToString(image))
					.mediaType("image/png".equals(mediaType) ? Base64ImageSource.MediaType.IMAGE_PNG
							: Base64ImageSource.MediaType.IMAGE_JPEG)
					.build();
			String text = hint != null ? "El vecino eligió la categoría " + hint.name() + "." : "El vecino no eligió categoría.";
			Message response = client.messages().create(MessageCreateParams.builder()
					.model(model)
					.maxTokens(1024L)
					.system(SYSTEM_PROMPT)
					.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
					.addUserMessageOfContentBlockParams(List.of(ContentBlockParam.ofImage(source),
							ContentBlockParam.ofText(text)))
					.build());
			String body = response.content().stream()
					.flatMap(block -> block.text().stream())
					.map(t -> t.text())
					.collect(Collectors.joining());
			return parse(body, hint);
		}
		catch (RuntimeException ex) {
			log.warn("La IA de visión no respondió ({}); se usa el analizador de reglas.", ex.getClass().getSimpleName());
			return fallback.analyze(image, mediaType, hint);
		}
	}

	static PhotoAnalysis parse(String body, RiskCategory hint) {
		try {
			JsonNode n = JSON.readTree(body.substring(body.indexOf('{'), body.lastIndexOf('}') + 1));
			RiskCategory proposed;
			try {
				proposed = RiskCategory.valueOf(n.path("category").asText());
			}
			catch (IllegalArgumentException unknown) {
				proposed = RiskCategory.STREET_FLOODING;
			}
			Integer water = n.path("waterLevelCm").isNumber() ? n.path("waterLevelCm").asInt() : null;
			double severity = Math.max(0, Math.min(1, n.path("severity").asDouble(0.5)));
			String summary = n.path("summary").asText("");
			return new PhotoAnalysis(hint != null ? hint : proposed, water, severity,
					summary.isBlank() ? RuleBasedPhotoRiskAnalyzer.summary(proposed) : summary);
		}
		catch (Exception ex) {
			throw new IllegalStateException("JSON inválido de la IA de visión", ex);
		}
	}
}
