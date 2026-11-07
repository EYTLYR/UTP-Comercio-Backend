package eytlyr.com.utp_comercio_backend.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapabilityRepository extends JpaRepository<Capability, UUID> {

	List<Capability> findByUserIdAndRemovedByUserFalseOrderByCreatedAtDesc(UUID userId);

	Optional<Capability> findByIdAndUserId(UUID id, UUID userId);

	/** El vecino retiró su consentimiento: todo lo aprendido de él queda fuera del inventario. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Capability c set c.removedByUser = true where c.user.id = :userId and c.removedByUser = false")
	int removeAllByUserId(@Param("userId") UUID userId);

}
