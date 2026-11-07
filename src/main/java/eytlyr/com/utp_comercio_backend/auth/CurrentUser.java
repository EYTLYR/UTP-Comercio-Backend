package eytlyr.com.utp_comercio_backend.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.common.UnauthorizedException;
import eytlyr.com.utp_comercio_backend.user.Role;

/** Datos del usuario autenticado, leídos del JWT de la petición actual. */
@Component
public class CurrentUser {

	public UUID id() {
		return UUID.fromString(token().getToken().getSubject());
	}

	public Role role() {
		return Role.valueOf(token().getToken().getClaimAsString(JwtService.CLAIM_ROLE));
	}

	public boolean hasRole(Role role) {
		return role() == role;
	}

	private JwtAuthenticationToken token() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken jwt) {
			return jwt;
		}
		throw new UnauthorizedException("UNAUTHORIZED", "Inicia sesión para continuar.");
	}
}
