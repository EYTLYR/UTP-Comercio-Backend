package eytlyr.com.utp_comercio_backend.prevention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.ai.GeminiClient;

/**
 * Análisis de la foto de riesgo (lluvias / El Niño) con Gemini (nearby.ai.provider=gemini). Envía la imagen
 * en Base64 y pide solo JSON con el mismo formato que el analizador de Claude. Si Gemini falla, tarda o
 * el circuito está abierto, responde el analizador de reglas: el vecino nunca recibe un 500.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "nearby.ai", name = "provider", havingValue = "gemini")
public class GeminiPhotoRiskAnalyzer implements PhotoRiskAnalyzer {

	private static final Logger log = LoggerFactory.getLogger(GeminiPhotoRiskAnalyzer.class);

	private final GeminiClient gemini;

	private final PhotoRiskAnalyzer fallback;

	public GeminiPhotoRiskAnalyzer(GeminiClient gemini,
			@Qualifier("ruleBasedPhotoRiskAnalyzer") PhotoRiskAnalyzer fallback) {
		this.gemini = gemini;
		this.fallback = fallback;
	}

	@Override
	public PhotoAnalysis analyze(byte[] image, String mediaType, RiskCategory hint) {
		try {
			String text = hint != null ? "El vecino eligió la categoría " + hint.name() + "."
					: "El vecino no eligió categoría.";
			String json = gemini.generateJson(AnthropicPhotoRiskAnalyzer.SYSTEM_PROMPT, text,
					new GeminiClient.Image(image, "image/png".equals(mediaType) ? "image/png" : "image/jpeg"));
			return AnthropicPhotoRiskAnalyzer.parse(json, hint);
		}
		catch (RuntimeException ex) {
			log.warn("Gemini no analizó la foto ({}); se usa el analizador de reglas.", ex.getMessage());
			return fallback.analyze(image, mediaType, hint);
		}
	}
}
