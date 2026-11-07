package eytlyr.com.utp_comercio_backend.ai;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionAssignmentRepository extends JpaRepository<MissionAssignment, UUID> {

	@EntityGraph(attributePaths = { "user", "capability", "report" })
	List<MissionAssignment> findByPlanIdOrderByCreatedAt(UUID planId);

	@EntityGraph(attributePaths = { "user", "capability" })
	List<MissionAssignment> findByPlanIdAndReportIdOrderByCreatedAt(UUID planId, UUID reportId);

}
