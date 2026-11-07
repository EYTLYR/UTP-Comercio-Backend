package eytlyr.com.utp_comercio_backend.zone;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StreetCodeRepository extends JpaRepository<StreetCode, UUID> {

	List<StreetCode> findByZoneId(UUID zoneId);

	Optional<StreetCode> findByZoneIdAndCode(UUID zoneId, String code);

}
