package eytlyr.com.utp_comercio_backend.emergency;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VulnerableReportRepository extends JpaRepository<VulnerableReport, UUID> {

	boolean existsByReporterIdAndStatusIn(UUID reporterId, Collection<ReportStatus> statuses);

}
