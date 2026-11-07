package eytlyr.com.utp_comercio_backend.zone;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StreetCodeRepository extends JpaRepository<StreetCode, UUID> {

}
