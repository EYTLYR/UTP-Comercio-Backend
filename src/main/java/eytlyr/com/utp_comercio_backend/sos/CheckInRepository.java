package eytlyr.com.utp_comercio_backend.sos;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

}
