package eytlyr.com.utp_comercio_backend.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrepChecklistItemRepository extends JpaRepository<PrepChecklistItem, UUID> {

	List<PrepChecklistItem> findByUserId(UUID userId);

}
