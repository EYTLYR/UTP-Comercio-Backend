package eytlyr.com.utp_comercio_backend.municipality;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipalityRepository extends JpaRepository<Municipality, UUID> {

	Optional<Municipality> findFirstByRequestedByIdOrderByCreatedAtDesc(UUID userId);

	boolean existsByRucAndStatusNot(String ruc, Municipality.Status status);

	boolean existsByDistrictKeyAndStatusNot(String districtKey, Municipality.Status status);

	@EntityGraph(attributePaths = "requestedBy")
	List<Municipality> findTop200ByOrderByCreatedAtDesc();
}
