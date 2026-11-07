package eytlyr.com.utp_comercio_backend.sos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SosResponseRepository extends JpaRepository<SosResponse, UUID> {

	Optional<SosResponse> findByAlertIdAndResponderId(UUID alertId, UUID responderId);

	@EntityGraph(attributePaths = "responder")
	List<SosResponse> findByAlertIdOrderByCreatedAt(UUID alertId);

	long countByAlertIdAndStatus(UUID alertId, ResponseStatus status);

}
