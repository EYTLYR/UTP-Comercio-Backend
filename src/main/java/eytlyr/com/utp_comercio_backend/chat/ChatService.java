package eytlyr.com.utp_comercio_backend.chat;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.chat.dto.ChatDtos.ConversationSummaryDto;
import eytlyr.com.utp_comercio_backend.chat.dto.ChatDtos.MessageCreatedEvent;
import eytlyr.com.utp_comercio_backend.chat.dto.ChatDtos.MessageDto;
import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.Created;
import eytlyr.com.utp_comercio_backend.common.ForbiddenException;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.posts.Post;
import eytlyr.com.utp_comercio_backend.services.NeighborService;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

/** Chat entre vecinos: conversaciones, mensajes con idempotencia por clientId y "no leídos". */
@Service
public class ChatService {

	public static final String CHAT_FORBIDDEN = "CHAT_FORBIDDEN";

	static final OffsetDateTime EPOCH = OffsetDateTime.parse("1970-01-01T00:00:00Z");

	private final ConversationRepository conversations;

	private final ConversationMessageRepository messages;

	private final AppUserRepository users;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public ChatService(ConversationRepository conversations, ConversationMessageRepository messages,
			AppUserRepository users, ApplicationEventPublisher events, Clock clock) {
		this.conversations = conversations;
		this.messages = messages;
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	/** Crea u obtiene la conversación entre dos vecinos sobre un servicio o una publicación. */
	@Transactional
	public Conversation getOrCreate(AppUser me, AppUser other, Post post, NeighborService service, String contextText) {
		if (me.getId().equals(other.getId())) {
			throw new BusinessRuleException("CHAT_SELF", "No puedes abrir un chat contigo mismo.");
		}
		var existing = post != null ? conversations.findBetweenAboutPost(me.getId(), other.getId(), post.getId())
				: service != null ? conversations.findBetweenAboutService(me.getId(), other.getId(), service.getId())
				: conversations.findBetweenWithoutContext(me.getId(), other.getId());
		return existing.orElseGet(() -> conversations.save(Conversation.builder()
				.userA(me)
				.userB(other)
				.post(post)
				.service(service)
				.contextText(contextText)
				.build()));
	}

	/** Mensaje del sistema (sin remitente), por ejemplo "Le ofreciste ayuda: «...»". */
	@Transactional
	public MessageDto systemMessage(Conversation c, String text) {
		ConversationMessage m = messages.save(ConversationMessage.builder().conversation(c).text(text).build());
		MessageDto dto = toDto(m);
		events.publishEvent(new MessageCreatedEvent(dto, null));
		return dto;
	}

	@Transactional(readOnly = true)
	public List<ConversationSummaryDto> list(UUID userId) {
		return conversations.findAllOf(userId).stream()
				.map(c -> {
					AppUser other = c.other(userId);
					var last = messages.findFirstByConversationIdOrderByCreatedAtDesc(c.getId());
					OffsetDateTime read = c.readAt(userId);
					String serviceTitle = c.getService() != null ? c.getService().getTitle() : null;
					String serviceCategory = c.getService() != null && c.getService().getCategory() != null ? c.getService().getCategory().name() : null;
					String postType = c.getPost() != null && c.getPost().getType() != null ? c.getPost().getType().name() : null;
					String context = c.getContextText();
					if ((context == null || context.isBlank()) && serviceTitle != null) {
						context = "Sobre: " + serviceTitle;
					} else if ((context == null || context.isBlank()) && c.getPost() != null) {
						String postText = c.getPost().getText();
						context = "Publicación: " + (postText != null && postText.length() > 30 ? postText.substring(0, 27) + "..." : postText);
					} else if ((context == null || context.isBlank()) && last.isPresent()) {
						String lastText = last.get().getText();
						if (lastText != null) {
							var matcher = java.util.regex.Pattern.compile("(?:para:|interesa:)\\s*[«“'\"(]([^»”'\")]+)[»”'\")]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(lastText);
							if (matcher.find()) {
								context = "Favor: " + matcher.group(1).trim();
							} else if (lastText.toLowerCase().startsWith("aceptaste la ayuda de")) {
								context = "Ayuda mutua";
							}
						}
					}
					return new ConversationSummaryDto(c.getId(), other.getId(), other.getName(),
							TextUtils.initials(other.getName()), context,
							last.map(ConversationMessage::getText).orElse(null),
							last.map(ConversationMessage::getCreatedAt).orElse(c.getCreatedAt()),
							messages.countUnread(c.getId(), userId, read != null ? read : EPOCH),
							serviceTitle, serviceCategory, postType);
				})
				.sorted(Comparator.comparing(ConversationSummaryDto::lastAt).reversed())
				.toList();
	}

	/** Mensajes posteriores a "after" (para polling). Marca la conversación como leída. */
	@Transactional
	public List<MessageDto> messages(UUID userId, UUID conversationId, OffsetDateTime after, int limit) {
		Conversation c = participantOf(userId, conversationId);
		c.markRead(userId, OffsetDateTime.now(clock));
		return messages.findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(conversationId,
						after != null ? after : EPOCH, PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
				.stream()
				.map(ChatService::toDto)
				.toList();
	}

	@Transactional
	public Created<MessageDto> send(UUID userId, UUID conversationId, UUID clientId, String text) {
		Conversation c = participantOf(userId, conversationId);
		if (clientId != null) {
			var existing = messages.findBySenderIdAndClientId(userId, clientId);
			if (existing.isPresent()) {
				return new Created<>(toDto(existing.get()), false);
			}
		}
		AppUser me = users.getReferenceById(userId);
		ConversationMessage m = messages.save(ConversationMessage.builder()
				.conversation(c)
				.sender(me)
				.text(text.trim())
				.clientId(clientId)
				.build());
		c.markRead(userId, OffsetDateTime.now(clock));
		MessageDto dto = toDto(m);
		events.publishEvent(new MessageCreatedEvent(dto, c.other(userId).getId()));
		return new Created<>(dto, true);
	}

	@Transactional(readOnly = true)
	public Conversation participantOf(UUID userId, UUID conversationId) {
		Conversation c = conversations.findById(conversationId).orElseThrow(() -> NotFoundException.of("la conversación"));
		if (!c.isParticipant(userId)) {
			throw new ForbiddenException(CHAT_FORBIDDEN, "Esta conversación no es tuya.");
		}
		return c;
	}

	static MessageDto toDto(ConversationMessage m) {
		AppUser s = m.getSender();
		return new MessageDto(m.getId(), m.getConversation().getId(), s != null ? s.getId() : null,
				s != null ? s.getName() : null, s == null, m.getText(), m.getCreatedAt());
	}
}
