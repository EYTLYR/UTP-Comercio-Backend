package eytlyr.com.utp_comercio_backend.posts;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostResponseRepository extends JpaRepository<PostResponse, UUID> {

	boolean existsByPostIdAndResponderId(UUID postId, UUID responderId);

	@Query("select r.post.id from PostResponse r where r.responder.id = :userId")
	Set<UUID> postIdsRespondedBy(@Param("userId") UUID userId);

	long countByPostZoneIdAndCreatedAtAfter(UUID zoneId, OffsetDateTime since);

	long countByResponderId(UUID responderId);

}
