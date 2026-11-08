package eytlyr.com.utp_comercio_backend.emergency;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRequestCandidateRepository extends JpaRepository<ResourceRequestCandidate, UUID> {

	@Query("""
			select c from ResourceRequestCandidate c
			join fetch c.request r
			join fetch r.requester
			join fetch c.listing
			where c.provider.id = :providerId
			  and c.status = 'PENDING'
			  and r.status = 'PENDING'
			order by c.notifiedAt desc
			""")
	List<ResourceRequestCandidate> findPendingByProviderId(@Param("providerId") UUID providerId);

	Optional<ResourceRequestCandidate> findByRequestIdAndProviderId(UUID requestId, UUID providerId);

	List<ResourceRequestCandidate> findByRequestId(UUID requestId);

	/**
	 * Cancela inmediatamente a todos los demás candidatos de una solicitud una vez aceptada.
	 */
	@Modifying
	@Query(value = """
			UPDATE resource_request_candidate
			SET status = 'CANCELLED', responded_at = now(), updated_at = now()
			WHERE request_id = :requestId AND provider_id != :acceptedProviderId AND status = 'PENDING'
			""", nativeQuery = true)
	int cancelOtherCandidates(
			@Param("requestId") UUID requestId,
			@Param("acceptedProviderId") UUID acceptedProviderId);

}

