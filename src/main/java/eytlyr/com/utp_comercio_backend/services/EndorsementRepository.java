package eytlyr.com.utp_comercio_backend.services;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EndorsementRepository extends JpaRepository<Endorsement, UUID> {

	boolean existsByServiceIdAndUserId(UUID serviceId, UUID userId);

	Optional<Endorsement> findByServiceIdAndUserId(UUID serviceId, UUID userId);

	@EntityGraph(attributePaths = "user")
	List<Endorsement> findTop4ByServiceIdOrderByCreatedAtDesc(UUID serviceId);

	@Query("select e.service.id from Endorsement e where e.user.id = :userId")
	Set<UUID> serviceIdsEndorsedBy(@Param("userId") UUID userId);

}
