package eytlyr.com.utp_comercio_backend.services;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NeighborServiceRepository extends JpaRepository<NeighborService, UUID> {

	@Query("select s from NeighborService s join fetch s.owner o where s.active = true and o.zone.id = :zoneId")
	List<NeighborService> findActiveInZone(@Param("zoneId") UUID zoneId);

	@Query("select s from NeighborService s join fetch s.owner where s.active = true")
	List<NeighborService> findAllActive();

	/** Oficios activos cerca de un punto, de cualquier zona. */
	@Query("select s from NeighborService s join fetch s.owner where s.active = true "
			+ "and s.lat between :minLat and :maxLat and s.lng between :minLng and :maxLng")
	List<NeighborService> findActiveNear(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLng") double minLng, @Param("maxLng") double maxLng);

	@Query(value = """
			SELECT s.* FROM neighbor_service s
			WHERE s.active = true
			  AND ST_Intersects(
			      ST_SetSRID(ST_MakePoint(s.lng, s.lat), 4326),
			      ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
			  )
			""", nativeQuery = true)
	List<NeighborService> findActiveInBoundingBoxNative(@Param("minLon") double minLon,
			@Param("minLat") double minLat,
			@Param("maxLon") double maxLon,
			@Param("maxLat") double maxLat);

}
