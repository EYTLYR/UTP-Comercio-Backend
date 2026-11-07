package eytlyr.com.utp_comercio_backend.emergency.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.emergency.ListingKind;
import eytlyr.com.utp_comercio_backend.emergency.Priority;
import eytlyr.com.utp_comercio_backend.emergency.ReportSource;
import eytlyr.com.utp_comercio_backend.emergency.ReportStatus;
import eytlyr.com.utp_comercio_backend.emergency.ResourceType;
import eytlyr.com.utp_comercio_backend.emergency.SafePointType;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;

/** Tablero humanitario, recursos críticos, grupos de apoyo y puntos seguros. */
public final class EmergencyDtos {

	private EmergencyDtos() {
	}

	public record DashboardDto(long reportsTotal, long pending, long onTheWay, long attended, long safeCheckIns,
			long activeSos) {
	}

	/** Para NEIGHBOR: dirección sin número y coordenadas aproximadas. Nunca incluye a quien reportó. */
	public record VulnerableReportDto(UUID id, VulnerableCategory category, String categoryLabel, String detail,
			Priority priority, ReportStatus status, String assignedLabel, boolean assignedToMe, ReportSource source,
			OffsetDateTime createdAt, String addressText, double lat, double lng, boolean exactLocation) {
	}

	public record VulnerableReportRequest(UUID clientId,
			@NotNull(message = "Elige la categoría") VulnerableCategory category,
			@Size(max = 1000, message = "El detalle no debe pasar de 1000 caracteres") String detail,
			Priority priority, Double lat, Double lng,
			@Size(max = 200, message = "La dirección es muy larga") String addressText) {
	}

	public record ReportStatusRequest(@NotNull(message = "Falta el estado") ReportStatus status) {
	}

	public record ResourceSummaryDto(String type, String label, long have, long need) {
	}

	public record ResourceDto(UUID id, ResourceType resourceType, MitigationType mitigationType, String label,
			ListingKind kind, String text, int quantity, String locationText, String ownerName, Integer distanceM,
			boolean mine, OffsetDateTime createdAt) {
	}

	public record ResourceRequest(
			@NotNull(message = "Elige el tipo de recurso") ResourceType resourceType,
			@NotNull(message = "Indica si tienes o necesitas") ListingKind kind,
			@NotBlank(message = "Describe el recurso") @Size(max = 500) String text,
			@Min(value = 0, message = "La cantidad no puede ser negativa") Integer quantity,
			@Size(max = 200) String locationText) {
	}

	public record SupportGroupDto(UUID id, String name, String icon, String description, boolean activeNow,
			String scheduleText, String contactPhone, int membersCount, boolean joinedByMe) {
	}

	public record SafePointDto(UUID id, String name, SafePointType type, Integer capacity, Integer occupied,
			double lat, double lng, Integer distanceM) {
	}
}
