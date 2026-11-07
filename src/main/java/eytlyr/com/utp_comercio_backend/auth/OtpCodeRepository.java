package eytlyr.com.utp_comercio_backend.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

	long countByPhoneAndCreatedAtAfter(String phone, OffsetDateTime since);

	Optional<OtpCode> findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(String phone);

	/** Invalida los códigos pendientes del número (al pedir uno nuevo solo vale el último). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update OtpCode o set o.consumed = true where o.phone = :phone and o.consumed = false")
	int invalidatePending(@Param("phone") String phone);

}
