package eytlyr.com.utp_comercio_backend.emergency;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VulnerableReportRepository extends JpaRepository<VulnerableReport, UUID> {

	boolean existsByReporterIdAndStatusIn(UUID reporterId, Collection<ReportStatus> statuses);

	Optional<VulnerableReport> findByReporterIdAndClientId(UUID reporterId, UUID clientId);

	long countByZoneId(UUID zoneId);

	long countByZoneIdAndStatus(UUID zoneId, ReportStatus status);

	/** El más reciente primero (el servicio ordena luego por prioridad). */
	@EntityGraph(attributePaths = "assignedTo")
	@Query("""
			select r from VulnerableReport r where r.zone.id = :zoneId and r.status in :statuses
			order by r.createdAt desc
			""")
	List<VulnerableReport> findInZone(@Param("zoneId") UUID zoneId, @Param("statuses") Collection<ReportStatus> statuses);

}
