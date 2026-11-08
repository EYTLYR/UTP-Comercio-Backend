package eytlyr.com.utp_comercio_backend.posts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, UUID> {

	Optional<Post> findByAuthorIdAndClientId(UUID authorId, UUID clientId);

	List<Post> findByZoneId(UUID zoneId);

	/** Publicaciones cerca de un punto, de cualquier zona (la caja se afina luego con la distancia real). */
	@Query("""
			select p from Post p join fetch p.author
			where p.status in :statuses and p.lat between :minLat and :maxLat and p.lng between :minLng and :maxLng
			order by p.createdAt desc
			""")
	List<Post> findNear(@Param("minLat") double minLat, @Param("maxLat") double maxLat, @Param("minLng") double minLng,
			@Param("maxLng") double maxLng, @Param("statuses") Collection<PostStatus> statuses);

	@Query(value = """
			SELECT p.* FROM post p
			WHERE p.status IN (:statuses)
			  AND ST_Intersects(
			      ST_SetSRID(ST_MakePoint(p.lng, p.lat), 4326),
			      ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
			  )
			ORDER BY p.created_at DESC
			""", nativeQuery = true)
	List<Post> findInBoundingBoxNative(@Param("minLon") double minLon,
			@Param("minLat") double minLat,
			@Param("maxLon") double maxLon,
			@Param("maxLat") double maxLat,
			@Param("statuses") Collection<String> statuses);

	@Query("""
			select p from Post p join fetch p.author
			where p.zone.id = :zoneId and p.status in :statuses
			order by p.createdAt desc
			""")
	List<Post> findInZone(@Param("zoneId") UUID zoneId, @Param("statuses") Collection<PostStatus> statuses);

}
