package eytlyr.com.utp_comercio_backend.ai;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RescuePlanRepository extends JpaRepository<RescuePlan, UUID> {

	Optional<RescuePlan> findFirstByZoneIdOrderByGeneratedAtDesc(UUID zoneId);

}
