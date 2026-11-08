package eytlyr.com.utp_comercio_backend.zone;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneStatsRepository extends JpaRepository<ZoneStats, UUID> {

	@Modifying
	@Query(value = """
			INSERT INTO zone_stats (zone_id, favors_resolved, created_at, updated_at)
			VALUES (:zoneId, 1, now(), now())
			ON CONFLICT (zone_id) DO UPDATE SET
			    favors_resolved = zone_stats.favors_resolved + 1,
			    updated_at = now()
			""", nativeQuery = true)
	void incrementFavorsResolved(@Param("zoneId") UUID zoneId);

}

