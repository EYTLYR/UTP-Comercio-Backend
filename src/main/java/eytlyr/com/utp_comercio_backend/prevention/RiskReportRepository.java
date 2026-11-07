package eytlyr.com.utp_comercio_backend.prevention;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskReportRepository extends JpaRepository<RiskReport, UUID> {

	@Query("""
			select r from RiskReport r join fetch r.reporter
			where r.zone.id = :zoneId and r.createdAt > :since
			order by r.createdAt desc
			""")
	List<RiskReport> findRecentInZone(@Param("zoneId") UUID zoneId, @Param("since") OffsetDateTime since);

	Optional<RiskReport> findByReporterIdAndClientId(UUID reporterId, UUID clientId);

	@Query("select r from RiskReport r join fetch r.reporter where r.riskZone.id = :riskZoneId order by r.createdAt desc")
	List<RiskReport> findByRiskZone(@Param("riskZoneId") UUID riskZoneId);

}
