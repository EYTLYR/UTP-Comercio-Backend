package eytlyr.com.utp_comercio_backend.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByPhone(String phone);

	List<AppUser> findByZoneId(UUID zoneId);

	List<AppUser> findByZoneIdAndRole(UUID zoneId, Role role);

	/**
	 * Vecinos cuya última ubicación está a {@code radiusM} metros o menos de (lat, lng), del más cercano
	 * al más lejano. ST_DWithin sobre geography mide en metros sobre el elipsoide y usa el índice GIST
	 * ix_app_user_location_geog. ST_MakePoint recibe (longitud, latitud).
	 */
	@Query(value = """
			SELECT u.* FROM app_user u
			WHERE u.location IS NOT NULL
			  AND ST_DWithin(u.location::geography,
			                 ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
			                 :radiusM)
			ORDER BY u.location::geography <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
			""", nativeQuery = true)
	List<AppUser> findWithinRadius(@Param("lat") double lat, @Param("lng") double lng,
			@Param("radiusM") double radiusM);

	/**
	 * Vecinos en el anillo entre {@code innerM} (excluido) y {@code outerM} metros: los que se avisan
	 * cuando alguien no puede ir a un SOS (de 500 m a 1 km).
	 */
	@Query(value = """
			SELECT u.* FROM app_user u
			WHERE u.location IS NOT NULL
			  AND ST_DWithin(u.location::geography,
			                 ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
			                 :outerM)
			  AND NOT ST_DWithin(u.location::geography,
			                     ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
			                     :innerM)
			""", nativeQuery = true)
	List<AppUser> findInRing(@Param("lat") double lat, @Param("lng") double lng, @Param("innerM") double innerM,
			@Param("outerM") double outerM);

}
