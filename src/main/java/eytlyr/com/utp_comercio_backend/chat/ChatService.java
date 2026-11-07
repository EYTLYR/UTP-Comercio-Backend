package eytlyr.com.utp_comercio_backend.chat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.posts.Post;
import eytlyr.com.utp_comercio_backend.services.NeighborService;
import eytlyr.com.utp_comercio_backend.user.AppUser;

/** Conversaciones entre vecinos. Los mensajes y el tiempo real se completan en la FASE 6. */
@Service
public class ChatService {

	private final ConversationRepository conversations;

	public ChatService(ConversationRepository conversations) {
		this.conversations = conversations;
	}

	/** Crea u obtiene la conversación entre dos vecinos sobre un servicio o una publicación. */
	@Transactional
	public Conversation getOrCreate(AppUser me, AppUser other, Post post, NeighborService service, String contextText) {
		if (me.getId().equals(other.getId())) {
			throw new BusinessRuleException("CHAT_SELF", "No puedes abrir un chat contigo mismo.");
		}
		return conversations.findBetween(me.getId(), other.getId(), post != null ? post.getId() : null,
						service != null ? service.getId() : null)
				.orElseGet(() -> conversations.save(Conversation.builder()
						.userA(me)
						.userB(other)
						.post(post)
						.service(service)
						.contextText(contextText)
						.build()));
	}
}
