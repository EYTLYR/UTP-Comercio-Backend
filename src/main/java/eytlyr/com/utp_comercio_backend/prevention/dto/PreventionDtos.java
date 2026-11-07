package eytlyr.com.utp_comercio_backend.prevention.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.emergency.ListingKind;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;
import eytlyr.com.utp_comercio_backend.prevention.RiskCategory;
import eytlyr.com.utp_comercio_backend.prevention.RiskLevel;

/** Radar preventivo, reportes de riesgo, mapa de calor y mitigación (El Niño). */
public final class PreventionDtos {

	private PreventionDtos() {
	}

	public record RiskReportRequest(UUID clientId, RiskCategory category,
			@Size(max = 1000, message = "El texto no debe pasar de 1000 caracteres") String text, Double lat, Double lng,
			@Size(max = 200, message = "La dirección es muy larga") String addressText) {
	}

	public record RiskReportDto(UUID id, RiskCategory category, String label, String text, String authorName,
			String addressText, String photoUrl, String photoAnalysis, Double severityHint, long minutesAgo,
			UUID riskZoneId, double lat, double lng) {
	}

	public record PhotoAnalysisDto(RiskCategory category, Integer waterLevelCm, double severity, String summary) {
	}

	public record ZoneReportItem(UUID id, RiskCategory category, String label, String text, String authorName,
			long minutesAgo, boolean hasPhoto) {
	}

	public record MitigationItem(UUID userId, String userName, List<String> labels, int distanceM, String evidence,
			boolean coordinated) {
	}

	public record RiskZoneDto(UUID id, String name, RiskLevel level, double centroidLat, double centroidLng,
			int reportCount, double score, String pattern, int homesEstimate, String alertText,
			List<ZoneReportItem> reports, List<MitigationItem> mitigation) {
	}

	/** escalated = la zona del reporte subió a RED con este reporte (el momento WOW de la demo). */
	public record RiskReportCreated(RiskReportDto report, PhotoAnalysisDto photoAnalysis, RiskZoneDto riskZone,
			boolean escalated) {
	}

	public record AnalyzeResult(long durationMs, List<RiskZoneDto> zones, List<UUID> escalated) {
	}

	public record CoordinateResult(UUID conversationId, int notifiedHomes) {
	}

	public record HeatSpot(UUID riskZoneId, double lat, double lng, int radiusM, RiskLevel level, String label) {
	}

	public record MitigationRequest(
			@NotNull(message = "Elige el recurso de mitigación") MitigationType mitigationType,
			@NotNull(message = "Indica si tienes o necesitas") ListingKind kind,
			@NotBlank(message = "Describe el recurso") @Size(max = 500) String text,
			@Min(value = 0, message = "La cantidad no puede ser negativa") Integer quantity,
			@Size(max = 200) String locationText) {
	}
}
