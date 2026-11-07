package eytlyr.com.utp_comercio_backend.services;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

	@EntityGraph(attributePaths = "author")
	List<Review> findByServiceIdOrderByCreatedAtDesc(UUID serviceId, Pageable pageable);

}
