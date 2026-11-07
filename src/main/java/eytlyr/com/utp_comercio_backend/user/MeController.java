package eytlyr.com.utp_comercio_backend.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.user.dto.UserDto;

/** Perfil propio. El resto de /me (ubicación, contactos, ficha) llega en la FASE 4. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Mi perfil")
public class MeController {

	private final UserService users;

	private final CurrentUser currentUser;

	public MeController(UserService users, CurrentUser currentUser) {
		this.users = users;
		this.currentUser = currentUser;
	}

	@GetMapping
	@Operation(summary = "Mi perfil y el modo que la app debe mostrar (effectiveMode)")
	public UserDto me() {
		return users.me(currentUser.id());
	}
}
