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

}
