package eytlyr.com.utp_comercio_backend.services;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EndorsementRepository extends JpaRepository<Endorsement, UUID> {

}
