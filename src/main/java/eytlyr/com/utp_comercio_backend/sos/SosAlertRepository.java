package eytlyr.com.utp_comercio_backend.sos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SosAlertRepository extends JpaRepository<SosAlert, UUID> {

	boolean existsByUserIdAndStatus(UUID userId, SosStatus status);

	@EntityGraph(attributePaths = { "user", "zone" })
	Optional<SosAlert> findByUserIdAndClientId(UUID userId, UUID clientId);

	@EntityGraph(attributePaths = { "user", "zone" })
	@Query("select a from SosAlert a where a.id = :id")
	Optional<SosAlert> findWithUser(@Param("id") UUID id);

	long countByZoneIdAndStatus(UUID zoneId, SosStatus status);

	long countByZoneIdAndStatusAndCreatedAtAfter(UUID zoneId, SosStatus status, OffsetDateTime since);

	@Query("select a from SosAlert a join fetch a.user where a.zone.id = :zoneId and a.status = :status")
	List<SosAlert> findByZoneAndStatus(@Param("zoneId") UUID zoneId, @Param("status") SosStatus status);

	/** SOS activos de otros vecinos a radiusM metros o menos, del más cercano al más lejano (PostGIS). */
	@Query(value = """
			SELECT a.* FROM sos_alert a
			WHERE a.status = 'ACTIVE'
			  AND a.user_id <> :userId
			  AND ST_DWithin(a.location::geography,
			                 ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
			                 :radiusM)
			ORDER BY a.location::geography <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
			""", nativeQuery = true)
	List<SosAlert> findActiveWithinRadius(@Param("userId") UUID userId, @Param("lat") double lat,
			@Param("lng") double lng, @Param("radiusM") double radiusM);

}
