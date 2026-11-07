package eytlyr.com.utp_comercio_backend.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.user.dto.UserDto;

@Service
public class UserService {

	private final AppUserRepository users;

	public UserService(AppUserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public UserDto me(UUID userId) {
		return UserDto.from(get(userId));
	}

	AppUser get(UUID userId) {
		return users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
	}
}
