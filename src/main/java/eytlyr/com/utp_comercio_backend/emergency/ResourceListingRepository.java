package eytlyr.com.utp_comercio_backend.emergency;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceListingRepository extends JpaRepository<ResourceListing, UUID> {

	@Query("""
			select l from ResourceListing l join fetch l.user
			where l.zone.id = :zoneId and l.active = true
			order by l.createdAt desc
			""")
	List<ResourceListing> findActiveInZone(@Param("zoneId") UUID zoneId);

	/**
	 * Encuentra los 3 proveedores más cercanos de un recurso específico con PostGIS ST_Distance.
	 */
	@Query(value = """
			SELECT l.* FROM resource_listing l
			WHERE l.kind = 'HAVE'
			  AND l.active = true
			  AND l.user_id != :requesterId
			  AND (cast(:resourceType as varchar) IS NULL OR l.resource_type = cast(:resourceType as varchar))
			  AND (cast(:mitigationType as varchar) IS NULL OR l.mitigation_type = cast(:mitigationType as varchar))
			ORDER BY ST_Distance(
			    ST_SetSRID(ST_MakePoint(l.lng, l.lat), 4326)::geography,
			    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
			) ASC
			LIMIT 3
			""", nativeQuery = true)
	List<ResourceListing> findTop3ClosestProviders(
			@Param("requesterId") UUID requesterId,
			@Param("resourceType") String resourceType,
			@Param("mitigationType") String mitigationType,
			@Param("lat") double lat,
			@Param("lng") double lng);

}
