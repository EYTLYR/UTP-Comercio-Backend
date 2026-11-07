package eytlyr.com.utp_comercio_backend.services;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

	@EntityGraph(attributePaths = "author")
	List<Review> findByServiceIdOrderByCreatedAtDesc(UUID serviceId, Pageable pageable);

	@Query("select r from Review r join fetch r.service s join fetch s.owner o join fetch r.author where o.zone.id = :zoneId")
	List<Review> findInZone(@Param("zoneId") UUID zoneId);

}
