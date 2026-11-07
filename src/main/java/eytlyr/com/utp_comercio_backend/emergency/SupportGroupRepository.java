package eytlyr.com.utp_comercio_backend.emergency;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportGroupRepository extends JpaRepository<SupportGroup, UUID> {

	List<SupportGroup> findByZoneIdOrderByName(UUID zoneId);

}
