package eytlyr.com.utp_comercio_backend.prevention;

/** IA con visión: estima el tipo de riesgo y su severidad a partir de la foto del vecino. */
public interface PhotoRiskAnalyzer {

	/** severity va de 0 a 1; waterLevelCm puede ser null. */
	record PhotoAnalysis(RiskCategory category, Integer waterLevelCm, double severity, String summary) {
	}

	/**
	 * @param hint categoría que eligió el vecino (puede ser null: la IA la propone)
	 */
	PhotoAnalysis analyze(byte[] image, String mediaType, RiskCategory hint);
}
