package eytlyr.com.utp_comercio_backend.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByPhone(String phone);

	List<AppUser> findByZoneId(UUID zoneId);

	List<AppUser> findByZoneIdAndRole(UUID zoneId, Role role);

}
