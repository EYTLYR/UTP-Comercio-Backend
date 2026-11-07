package eytlyr.com.utp_comercio_backend.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	/** Conversación entre dos vecinos sobre el mismo servicio o publicación (o sin contexto). */
	@Query("""
			select c from Conversation c left join c.post p left join c.service s
			where ((c.userA.id = :a and c.userB.id = :b) or (c.userA.id = :b and c.userB.id = :a))
			  and ((:postId is null and p is null) or p.id = :postId)
			  and ((:serviceId is null and s is null) or s.id = :serviceId)
			""")
	Optional<Conversation> findBetween(@Param("a") UUID a, @Param("b") UUID b, @Param("postId") UUID postId,
			@Param("serviceId") UUID serviceId);

	@Query("""
			select c from Conversation c join fetch c.userA join fetch c.userB
			where c.userA.id = :userId or c.userB.id = :userId
			""")
	List<Conversation> findAllOf(@Param("userId") UUID userId);

}
