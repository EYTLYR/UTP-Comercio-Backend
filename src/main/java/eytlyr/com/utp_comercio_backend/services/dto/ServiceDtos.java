package eytlyr.com.utp_comercio_backend.services.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.services.ServiceCategory;

/** Peticiones y respuestas de /api/v1/services. */
public final class ServiceDtos {

	private ServiceDtos() {
	}

	public record ServiceSummaryDto(UUID id, String ownerName, String title, ServiceCategory category,
			String categoryLabel, Integer distanceM, int endorsementCount, boolean endorsedByMe,
			boolean availableInEmergency, String priceText) {
	}

	public record ReviewDto(UUID id, String authorName, String authorInitials, String text, OffsetDateTime createdAt) {
	}

	public record ServiceDetailDto(UUID id, String ownerName, String title, ServiceCategory category,
			String categoryLabel, Integer distanceM, int endorsementCount, boolean endorsedByMe,
			boolean availableInEmergency, String priceText, String description, String scheduleText,
			boolean ownerVerified, String zoneName, List<String> endorserInitials, List<ReviewDto> latestReviews,
			String ownerPhoneMasked, boolean mine) {
	}

	public record ServiceRequest(
			@NotBlank(message = "Escribe el nombre de tu oficio") @Size(max = 120) String title,
			@NotNull(message = "Elige una categoría") ServiceCategory category,
			@Size(max = 1000, message = "La descripción no debe pasar de 1000 caracteres") String description,
			@Size(max = 80) String priceText,
			@Size(max = 80) String scheduleText,
			boolean availableInEmergency,
			// Dónde está el vecino al publicar. Si no llega, se usa su casa.
			Double lat,
			Double lng) {
	}

	public record ReviewRequest(
			@NotBlank(message = "Escribe tu reseña")
			@Size(min = 5, max = 500, message = "La reseña debe tener entre 5 y 500 caracteres") String text) {
	}

	public record EndorsementResult(UUID serviceId, int endorsementCount, boolean endorsedByMe) {
	}

	public record ConversationRef(UUID conversationId) {
	}
}
