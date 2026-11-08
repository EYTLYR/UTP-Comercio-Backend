package eytlyr.com.utp_comercio_backend.emergency;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SafePointRepository extends JpaRepository<SafePoint, UUID> {

	List<SafePoint> findByZoneIdOrderByName(UUID zoneId);

	/**
	 * Reserva de cupos atómica a nivel de base de datos con verificación de capacidad disponible.
	 * Retorna 1 si se reservó exitosamente, o 0 si no hay cupos suficientes o no existe el refugio.
	 */
	@Modifying
	@Query(value = """
			UPDATE safe_point
			SET occupied = COALESCE(occupied, 0) + :count,
			    version = version + 1,
			    updated_at = now()
			WHERE id = :id
			  AND (capacity - COALESCE(occupied, 0)) >= :count
			""", nativeQuery = true)
	int reserveSpotsNative(@Param("id") UUID id, @Param("count") int count);

}
