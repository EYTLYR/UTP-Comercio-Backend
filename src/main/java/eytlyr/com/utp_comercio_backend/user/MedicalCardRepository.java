package eytlyr.com.utp_comercio_backend.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalCardRepository extends JpaRepository<MedicalCard, UUID> {

	Optional<MedicalCard> findByUserId(UUID userId);

}
