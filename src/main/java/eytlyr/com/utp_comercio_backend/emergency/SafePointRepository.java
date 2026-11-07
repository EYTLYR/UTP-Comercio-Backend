package eytlyr.com.utp_comercio_backend.emergency;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SafePointRepository extends JpaRepository<SafePoint, UUID> {

	List<SafePoint> findByZoneIdOrderByName(UUID zoneId);

}
