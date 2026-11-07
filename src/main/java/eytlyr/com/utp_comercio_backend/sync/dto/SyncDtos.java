package eytlyr.com.utp_comercio_backend.sync.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.InventorySnapshot;
import eytlyr.com.utp_comercio_backend.emergency.SafePointType;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.SupportGroupDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.VulnerableReportDto;
import eytlyr.com.utp_comercio_backend.posts.PostCategory;
import eytlyr.com.utp_comercio_backend.posts.PostStatus;
import eytlyr.com.utp_comercio_backend.posts.PostType;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskZoneDto;
import eytlyr.com.utp_comercio_backend.services.ServiceCategory;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneDto;

/** Sincronización para el modo sin conexión de la app. */
public final class SyncDtos {

	private SyncDtos() {
	}

	public record ServiceItem(UUID id, String title, ServiceCategory category, String categoryLabel, String ownerName,
			int endorsementCount, boolean availableInEmergency, String priceText, double lat, double lng,
			OffsetDateTime updatedAt) {
	}

	public record SyncPost(UUID id, PostType type, PostCategory category, String text, String authorName,
			PostStatus status, double lat, double lng, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record SyncSafePoint(UUID id, String name, SafePointType type, Integer capacity, Integer occupied, double lat,
			double lng) {
	}

	public record SyncStreet(String code, String name, double lat, double lng) {
	}

	public record Deleted(List<UUID> posts, List<UUID> services) {
	}

	public record SyncResponse(OffsetDateTime serverTime, boolean full, ZoneDto zone, List<ServiceItem> services,
			List<SyncPost> posts, List<SyncSafePoint> safePoints, List<SupportGroupDto> supportGroups,
			List<SyncStreet> streetCodes, List<VulnerableReportDto> vulnerableReports, InventorySnapshot inventorySnapshot,
			List<RiskZoneDto> riskZones, Deleted deleted) {
	}

	public enum OutboxKind {
		POST, SOS, VULNERABLE_REPORT, RISK_REPORT, MESSAGE, CHECK_IN
	}

	/** payload es el mismo JSON que acepta el endpoint de ese tipo (MESSAGE agrega conversationId). */
	public record OutboxItem(@NotNull(message = "Falta el tipo") OutboxKind kind,
			@NotNull(message = "Falta el contenido") Map<String, Object> payload) {
	}

	public enum OutboxStatus {
		CREATED, DUPLICATE, ERROR
	}

	public record OutboxResult(UUID clientId, OutboxKind kind, OutboxStatus status, UUID id, String code, String error) {
	}
}
