package eytlyr.com.utp_comercio_backend.sos;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

	/** Vecinos distintos que dijeron "Estoy bien" desde la fecha indicada. */
	@Query("""
			select count(distinct c.user.id) from CheckIn c
			where c.zone.id = :zoneId and c.status = :status and c.createdAt > :since
			""")
	long countUsers(@Param("zoneId") UUID zoneId, @Param("status") CheckInStatus status,
			@Param("since") OffsetDateTime since);

}
