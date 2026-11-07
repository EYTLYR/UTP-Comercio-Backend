package eytlyr.com.utp_comercio_backend.prevention;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskZoneRepository extends JpaRepository<RiskZone, UUID> {

	List<RiskZone> findByZoneIdAndActiveTrueOrderByScoreDesc(UUID zoneId);

}
