package eytlyr.com.utp_comercio_backend.zone;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

	Optional<Zone> findByName(String name);

	boolean existsByName(String name);

	long countByCreatedBy(java.util.UUID createdBy);

}
