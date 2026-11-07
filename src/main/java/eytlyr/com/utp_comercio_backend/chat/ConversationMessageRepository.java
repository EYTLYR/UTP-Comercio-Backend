package eytlyr.com.utp_comercio_backend.chat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

	@EntityGraph(attributePaths = "sender")
	List<ConversationMessage> findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID conversationId,
			OffsetDateTime after, Pageable pageable);

	@EntityGraph(attributePaths = "sender")
	Optional<ConversationMessage> findBySenderIdAndClientId(UUID senderId, UUID clientId);

	Optional<ConversationMessage> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);

	/** Mensajes del otro o del sistema posteriores a mi última lectura. */
	@Query("""
			select count(m) from ConversationMessage m left join m.sender s
			where m.conversation.id = :conversationId and (s is null or s.id <> :userId)
			  and m.createdAt > :since
			""")
	long countUnread(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId,
			@Param("since") OffsetDateTime since);

}
