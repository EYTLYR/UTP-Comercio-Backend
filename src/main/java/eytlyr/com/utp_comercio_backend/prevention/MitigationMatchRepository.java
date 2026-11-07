package eytlyr.com.utp_comercio_backend.prevention;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MitigationMatchRepository extends JpaRepository<MitigationMatch, UUID> {

}
