package eytlyr.com.utp_comercio_backend.prevention;

import org.springframework.stereotype.Component;

/** Analizador de desarrollo: devuelve el texto PHOTO_AI del ANEXO B según la categoría (no mira la imagen). */
@Component("ruleBasedPhotoRiskAnalyzer")
public class RuleBasedPhotoRiskAnalyzer implements PhotoRiskAnalyzer {

	@Override
	public PhotoAnalysis analyze(byte[] image, String mediaType, RiskCategory hint) {
		RiskCategory category = hint != null ? hint : RiskCategory.STREET_FLOODING;
		return new PhotoAnalysis(category, category == RiskCategory.STREET_FLOODING ? 25 : null, severity(category),
				summary(category));
	}

	public static String summary(RiskCategory category) {
		return switch (category) {
			case ACEQUIA_OVERFLOW -> "Agua corriendo sobre la vereda, cauce al 95 %. Riesgo alto de desborde.";
			case STREET_FLOODING -> "Agua acumulada de unos 25 cm en la pista. Riesgo medio de aniego.";
			case SEEPAGE -> "Mancha de humedad y agua brotando del piso. Posible filtración subterránea.";
			case RAVINE_ACTIVITY -> "Lodo y piedras en el cauce. Posible activación de la quebrada. Riesgo alto.";
			case DRAIN_OVERFLOW -> "Buzón rebalsando con agua turbia. Riesgo medio de aniego.";
			case CRACK -> "Grieta diagonal de unos 2 cm en muro de contención. Riesgo alto.";
		};
	}

	public static double severity(RiskCategory category) {
		return switch (category) {
			case ACEQUIA_OVERFLOW, CRACK -> 0.85;
			case STREET_FLOODING -> 0.55;
			case SEEPAGE, DRAIN_OVERFLOW -> 0.5;
			case RAVINE_ACTIVITY -> 0.9;
		};
	}
}
