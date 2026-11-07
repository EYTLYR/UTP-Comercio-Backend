package eytlyr.com.utp_comercio_backend.chat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.chat.dto.ChatDtos.ConversationSummaryDto;
import eytlyr.com.utp_comercio_backend.chat.dto.ChatDtos.MessageDto;
import eytlyr.com.utp_comercio_backend.chat.dto.ChatDtos.SendMessageRequest;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Chat")
public class ChatController {

	private final ChatService chat;

	private final ChatStreamRegistry streams;

	private final CurrentUser currentUser;

	public ChatController(ChatService chat, ChatStreamRegistry streams, CurrentUser currentUser) {
		this.chat = chat;
		this.streams = streams;
		this.currentUser = currentUser;
	}

	@GetMapping
	@Operation(summary = "Mis conversaciones, la más reciente primero")
	public List<ConversationSummaryDto> list() {
		return chat.list(currentUser.id());
	}

	@GetMapping("/{id}/messages")
	@Operation(summary = "Mensajes (polling con after=ISO-8601). Solo participantes")
	public List<MessageDto> messages(@PathVariable UUID id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime after,
			@RequestParam(defaultValue = "50") int limit) {
		return chat.messages(currentUser.id(), id, after, limit);
	}

	@PostMapping("/{id}/messages")
	@Operation(summary = "Enviar un mensaje (idempotente por clientId)")
	public ResponseEntity<MessageDto> send(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest body) {
		return chat.send(currentUser.id(), id, body.clientId(), body.text()).toResponse();
	}

	@GetMapping(path = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "Mensajes nuevos en tiempo real (Server-Sent Events, opcional)")
	public SseEmitter stream(@PathVariable UUID id) {
		chat.participantOf(currentUser.id(), id);
		return streams.subscribe(id);
	}
}
