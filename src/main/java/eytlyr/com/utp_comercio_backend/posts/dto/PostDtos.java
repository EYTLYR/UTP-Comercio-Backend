package eytlyr.com.utp_comercio_backend.posts.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.posts.PostCategory;
import eytlyr.com.utp_comercio_backend.posts.PostStatus;
import eytlyr.com.utp_comercio_backend.posts.PostType;

/** Peticiones y respuestas del muro de favores. */
public final class PostDtos {

	private PostDtos() {
	}

	public record PostRequest(UUID clientId,
			@NotNull(message = "Elige si necesitas u ofreces") PostType type,
			@NotNull(message = "Elige una categoría") PostCategory category,
			@Size(max = 1000, message = "El texto no debe pasar de 1000 caracteres") String text,
			@Size(max = 500) String photoUrl,
			Double lat,
			Double lng) {
	}

	public record PostDto(UUID id, PostType type, PostCategory category, String categoryLabel, String text,
			String authorName, String authorInitials, Integer distanceM, OffsetDateTime createdAt,
			boolean respondedByMe, PostStatus status, boolean mine, long responsesCount, String authorZoneName,
			Double lat, Double lng) {
	}

	public record StatusRequest(@NotNull(message = "Falta el estado") PostStatus status) {
	}

	public record NeighborhoodStats(long favorsThisWeek, int monthlyGoal, long myFavors) {
	}
}
