package eytlyr.com.utp_comercio_backend.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas del chat. */
public final class ChatDtos {

	private ChatDtos() {
	}

	public record ConversationSummaryDto(UUID id, UUID otherUserId, String otherUserName, String otherInitials,
			String contextText, String lastMessage, OffsetDateTime lastAt, long unread,
			String serviceTitle, String serviceCategory, String postType) {
	}

	/** senderId null = mensaje del sistema. */
	public record MessageDto(UUID id, UUID conversationId, UUID senderId, String senderName, boolean system,
			String text, OffsetDateTime createdAt) {
	}

	public record SendMessageRequest(UUID clientId,
			@NotBlank(message = "Escribe un mensaje")
			@Size(max = 1000, message = "El mensaje no debe pasar de 1000 caracteres") String text) {
	}

	/** Se publica al guardar un mensaje; tras el commit se envía por SSE y se notifica al destinatario. */
	public record MessageCreatedEvent(MessageDto message, UUID recipientId) {
	}
}
