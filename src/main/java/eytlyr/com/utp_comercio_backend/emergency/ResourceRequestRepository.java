package eytlyr.com.utp_comercio_backend.emergency;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, UUID> {

	@Query("""
			select r from ResourceRequest r
			join fetch r.requester
			left join fetch r.acceptedBy
			left join fetch r.acceptedListing
			where r.id = :id
			""")
	Optional<ResourceRequest> findByIdWithDetails(@Param("id") UUID id);

	/**
	 * Actualización atómica de estado: solo pasa a ACCEPTED si sigue en PENDING.
	 * Retorna 1 si fue aceptado por este proveedor, 0 si ya fue aceptado por otro vecino o cancelado.
	 */
	@Modifying
	@Query(value = """
			UPDATE resource_request
			SET status = 'ACCEPTED', accepted_by_id = :providerId, accepted_listing_id = :listingId, updated_at = now()
			WHERE id = :id AND status = 'PENDING'
			""", nativeQuery = true)
	int acceptRequestAtomic(
			@Param("id") UUID id,
			@Param("providerId") UUID providerId,
			@Param("listingId") UUID listingId);

}

