package eytlyr.com.utp_comercio_backend.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.ai.CapabilityKind;
import eytlyr.com.utp_comercio_backend.ai.CapabilityTag;
import eytlyr.com.utp_comercio_backend.ai.SourceType;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;

/** Inventario de supervivencia, planes de rescate y clasificador (Protocolo Lázaro). */
public final class AiDtos {

	private AiDtos() {
	}

	/** Para NEIGHBOR, las vulnerabilidades llegan anónimas: "Vecino de Jr. Los Pinos", sin evidencia y aproximadas. */
	public record InventoryItemDto(UUID id, CapabilityTag tag, String label, CapabilityKind kind, String displayName,
			String zoneLabel, String evidenceText, SourceType sourceType, String sourceAgo, double lat, double lng) {
	}

	public record SnapshotItem(UUID id, CapabilityTag tag, CapabilityKind kind, UUID userId, String displayName,
			double lat, double lng) {
	}

	/** Versión compacta para guardar en el teléfono y asignar misiones sin internet. */
	public record InventorySnapshot(String version, OffsetDateTime generatedAt, List<SnapshotItem> items) {
	}

	public record InventoryStats(long resources, long skills, long preventiveAlerts) {
	}

	public record AssignmentDto(UUID userId, String userName, CapabilityTag tag, String label, int distanceM,
			String reason) {
	}

	public record MissionDto(UUID reportId, VulnerableCategory category, String reportSummary,
			List<AssignmentDto> assignments) {
	}

	public record RescuePlanDto(UUID planId, OffsetDateTime generatedAt, long durationMs, List<MissionDto> missions,
			int preventiveReports) {
	}

	public record NotifyResult(UUID reportId, String assignedLabel, int notified) {
	}

	public record ClassifyRequest(
			@NotBlank(message = "Falta el texto") @Size(max = 1000, message = "El texto es muy largo") String text,
			@NotNull(message = "Indica el modo: NORMAL, PREVENTION o EMERGENCY") ZoneMode mode) {
	}

	/**
	 * type: VULNERABLE | RESOURCE (emergencia), NEED | OFFER (día a día), RISK_REPORT | MITIGATION (prevención).
	 * kind: NEED | OFFER para RESOURCE y MITIGATION.
	 */
	public record ClassifyResult(String type, String kind, String category, String categoryLabel, String priority,
			String location, List<String> chips) {
	}

	/** Se publica cuando la zona pasa a EMERGENCY: dispara el plan de rescate automático. */
	public record ZoneEmergencyActivatedEvent(UUID zoneId) {
	}
}
