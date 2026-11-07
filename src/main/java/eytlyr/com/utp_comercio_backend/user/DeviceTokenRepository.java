package eytlyr.com.utp_comercio_backend.user;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

}
