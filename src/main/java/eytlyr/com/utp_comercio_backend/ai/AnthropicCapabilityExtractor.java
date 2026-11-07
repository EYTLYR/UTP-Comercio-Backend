package eytlyr.com.utp_comercio_backend.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
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
 * Extractor con Claude (nearby.ai.provider=anthropic). Pide solo JSON, descarta etiquetas desconocidas
 * y, si falla o tarda más de 3 s, usa el extractor de reglas. La API key nunca se escribe en el log.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "nearby.ai", name = "provider", havingValue = "anthropic")
public class AnthropicCapabilityExtractor implements CapabilityExtractor {

	private static final Logger log = LoggerFactory.getLogger(AnthropicCapabilityExtractor.class);

	private static final ObjectMapper JSON = new ObjectMapper();

	static final String SYSTEM_PROMPT = """
			Eres el motor de inventario de una app vecinal de San Juan de Lurigancho (Lima, Perú).
			Lee un texto que escribió un vecino (publicación, oficio, reseña o listado de recursos) y detecta
			capacidades útiles en una emergencia. Etiquetas permitidas:
			%s
			Reglas:
			- Solo marca lo que el texto dice de forma explícita. Si alguien NECESITA algo, no lo tiene:
			  en ese caso solo cuentan las vulnerabilidades (ELDERLY_AT_HOME, REDUCED_MOBILITY, OXYGEN_DEPENDENT).
			- "evidence" es la frase literal del texto que lo prueba.
			- "confidence" va de 0 a 1.
			Responde SOLO con JSON, sin texto adicional, con esta forma:
			{"capabilities":[{"tag":"<ETIQUETA>","confidence":0.0,"evidence":"<frase literal>"}]}
			Si no hay nada, responde {"capabilities":[]}.
			""".formatted(Arrays.stream(CapabilityTag.values())
			.map(t -> "- " + t.name() + ": " + t.label() + " (" + t.kind().name() + ")")
			.collect(Collectors.joining("\n")));

	private final AnthropicClient client;

	private final String model;

	private final CapabilityExtractor fallback;

	public AnthropicCapabilityExtractor(NearbyProperties props,
			@Qualifier("ruleBasedCapabilityExtractor") CapabilityExtractor fallback) {
		this.client = AnthropicOkHttpClient.builder()
				.apiKey(props.ai().apiKey())
				.timeout(Duration.ofSeconds(3))
				.maxRetries(0)
				.build();
		this.model = props.ai().model();
		this.fallback = fallback;
	}

	@Override
	public List<ExtractedCapability> extract(String text, SourceType source) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		try {
			Message response = client.messages().create(MessageCreateParams.builder()
					.model(model)
					.maxTokens(1024L)
					.system(SYSTEM_PROMPT)
					.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
					.addUserMessage("Tipo de contenido: " + source.name() + "\nTexto: " + text)
					.build());
			String body = response.content().stream()
					.flatMap(block -> block.text().stream())
					.map(t -> t.text())
					.collect(Collectors.joining());
			return parse(body);
		}
		catch (RuntimeException ex) {
			log.warn("La IA de Anthropic no respondió ({}); se usa el extractor de reglas.", ex.getClass().getSimpleName());
			return fallback.extract(text, source);
		}
	}

	/** Valida el JSON contra el enum: las etiquetas desconocidas se descartan. */
	static List<ExtractedCapability> parse(String body) {
		List<ExtractedCapability> out = new ArrayList<>();
		try {
			int start = body.indexOf('{');
			int end = body.lastIndexOf('}');
			if (start < 0 || end <= start) {
				throw new IllegalArgumentException("La respuesta no es JSON");
			}
			JsonNode caps = JSON.readTree(body.substring(start, end + 1)).path("capabilities");
			for (JsonNode c : caps) {
				CapabilityTag tag;
				try {
					tag = CapabilityTag.valueOf(c.path("tag").asText());
				}
				catch (IllegalArgumentException unknown) {
					continue;
				}
				double confidence = Math.max(0, Math.min(1, c.path("confidence").asDouble(0.7)));
				String evidence = RuleBasedCapabilityExtractor.truncate(c.path("evidence").asText(""));
				out.add(new ExtractedCapability(tag, confidence, evidence));
			}
			return out;
		}
		catch (Exception ex) {
			throw new IllegalStateException("JSON inválido de la IA", ex);
		}
	}
}
