package eytlyr.com.utp_comercio_backend.prevention;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MitigationMatchRepository extends JpaRepository<MitigationMatch, UUID> {

	@EntityGraph(attributePaths = { "user", "capability" })
	List<MitigationMatch> findByRiskZoneIdOrderByDistanceM(UUID riskZoneId);

	@Modifying(flushAutomatically = true)
	@Query("delete from MitigationMatch m where m.riskZone.id = :riskZoneId")
	int deleteByRiskZone(@Param("riskZoneId") UUID riskZoneId);

}
