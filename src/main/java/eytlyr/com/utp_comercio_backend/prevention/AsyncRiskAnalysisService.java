package eytlyr.com.utp_comercio_backend.prevention;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.prevention.PhotoRiskAnalyzer.PhotoAnalysis;

/**
 * Servicio en segundo plano (@Async) para análisis de fotos de riesgo con IA (Gemini).
 * Garantiza que la creación del reporte sea instantánea y no bloquee al usuario durante una tormenta.
 */
@Service
public class AsyncRiskAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AsyncRiskAnalysisService.class);

	private final RiskReportRepository reports;

	private final PhotoRiskAnalyzer analyzer;

	private final RiskClusteringService clustering;

	public AsyncRiskAnalysisService(RiskReportRepository reports, PhotoRiskAnalyzer analyzer,
			RiskClusteringService clustering) {
		this.reports = reports;
		this.analyzer = analyzer;
		this.clustering = clustering;
	}

	/**
	 * Análisis asíncrono en segundo plano con Gemini/IA para no bloquear la creación del reporte
	 * en tormentas o ante latencias de red. Si Gemini falla o se desconecta, el reporte se preserva intacto.
	 */
	@Async
	@Transactional
	public CompletableFuture<Void> analyzePhotoAsync(UUID reportId, byte[] photoBytes, String mediaType,
			RiskCategory originalCategory, UUID zoneId) {
		try {
			log.info("Iniciando análisis asíncrono con Gemini para reporte de riesgo {}", reportId);
			PhotoAnalysis analysis = analyzer.analyze(photoBytes, mediaType, originalCategory);
			if (analysis != null) {
				reports.findById(reportId).ifPresent(r -> {
					r.setPhotoAnalysis(analysis.summary());
					r.setSeverityHint(analysis.severity());
					// No sobreescribir la categoría manual elegida por el vecino
					reports.save(r);
					clustering.recompute(zoneId);
					log.info("Reporte {} actualizado asíncronamente con análisis de Gemini (severidad: {})", reportId,
							analysis.severity());
				});
			}
		}
		catch (Exception ex) {
			log.warn("El análisis asíncrono de Gemini falló para reporte {}: {}. El reporte se mantiene intacto con datos manuales.",
					reportId, ex.getMessage());
			reports.findById(reportId).ifPresent(r -> {
				if ("PENDING_AI_VERIFICATION".equals(r.getPhotoAnalysis())) {
					r.setPhotoAnalysis("AI_VERIFICATION_UNAVAILABLE");
					reports.save(r);
				}
			});
		}
		return CompletableFuture.completedFuture(null);
	}

}
