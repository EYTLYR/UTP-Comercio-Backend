package eytlyr.com.utp_comercio_backend.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	@Query("""
			select c from Conversation c
			where ((c.userA.id = :a and c.userB.id = :b) or (c.userA.id = :b and c.userB.id = :a))
			  and c.post.id = :postId
			""")
	Optional<Conversation> findBetweenAboutPost(@Param("a") UUID a, @Param("b") UUID b, @Param("postId") UUID postId);

	@Query("""
			select c from Conversation c
			where ((c.userA.id = :a and c.userB.id = :b) or (c.userA.id = :b and c.userB.id = :a))
			  and c.service.id = :serviceId
			""")
	Optional<Conversation> findBetweenAboutService(@Param("a") UUID a, @Param("b") UUID b,
			@Param("serviceId") UUID serviceId);

	@Query("""
			select c from Conversation c
			where ((c.userA.id = :a and c.userB.id = :b) or (c.userA.id = :b and c.userB.id = :a))
			  and c.post is null and c.service is null
			""")
	Optional<Conversation> findBetweenWithoutContext(@Param("a") UUID a, @Param("b") UUID b);

	@Query("""
			select c from Conversation c join fetch c.userA join fetch c.userB
			where c.userA.id = :userId or c.userB.id = :userId
			""")
	List<Conversation> findAllOf(@Param("userId") UUID userId);

}
