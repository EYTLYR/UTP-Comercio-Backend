package eytlyr.com.utp_comercio_backend.chat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import eytlyr.com.utp_comercio_backend.chat.dto.ChatDtos.MessageCreatedEvent;
import eytlyr.com.utp_comercio_backend.notification.NotificationService;

/**
 * Tiempo real opcional (SSE) y aviso push de mensajes nuevos, siempre después del commit.
 * La app también puede usar polling con ?after=.
 */
@Component
public class ChatStreamRegistry {

	private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

	private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

	private final NotificationService notifications;

	public ChatStreamRegistry(NotificationService notifications) {
		this.notifications = notifications;
	}

	public SseEmitter subscribe(UUID conversationId) {
		SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
		List<SseEmitter> list = emitters.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>());
		list.add(emitter);
		Runnable remove = () -> list.remove(emitter);
		emitter.onCompletion(remove);
		emitter.onTimeout(remove);
		emitter.onError(e -> remove.run());
		return emitter;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMessage(MessageCreatedEvent event) {
		var msg = event.message();
		for (SseEmitter emitter : emitters.getOrDefault(msg.conversationId(), List.of())) {
			try {
				emitter.send(SseEmitter.event().name("message").data(msg));
			}
			catch (IOException | IllegalStateException ex) {
				emitter.completeWithError(ex);
			}
		}
		if (event.recipientId() != null) {
			notifications.notifyUser(event.recipientId(), "Mensaje de " + msg.senderName(), msg.text(),
					Map.of("type", "chat", "conversationId", msg.conversationId().toString()));
		}
	}
}
