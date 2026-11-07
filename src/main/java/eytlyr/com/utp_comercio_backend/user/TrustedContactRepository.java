package eytlyr.com.utp_comercio_backend.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrustedContactRepository extends JpaRepository<TrustedContact, UUID> {

	List<TrustedContact> findByUserIdOrderByPosition(UUID userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from TrustedContact c where c.user.id = :userId")
	int deleteAllByUserId(@Param("userId") UUID userId);

}
