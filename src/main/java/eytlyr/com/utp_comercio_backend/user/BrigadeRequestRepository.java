package eytlyr.com.utp_comercio_backend.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrigadeRequestRepository extends JpaRepository<BrigadeRequest, UUID> {

	Optional<BrigadeRequest> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

	Optional<BrigadeRequest> findFirstByUserIdAndStatus(UUID userId, BrigadeRequest.Status status);

	@EntityGraph(attributePaths = "user")
	List<BrigadeRequest> findTop100ByStatusOrderByCreatedAtAsc(BrigadeRequest.Status status);
}
