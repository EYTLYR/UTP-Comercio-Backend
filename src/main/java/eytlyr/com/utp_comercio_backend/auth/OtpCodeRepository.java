package eytlyr.com.utp_comercio_backend.auth;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

}
