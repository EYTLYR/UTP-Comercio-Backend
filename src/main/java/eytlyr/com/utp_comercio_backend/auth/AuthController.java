package eytlyr.com.utp_comercio_backend.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.dto.AuthDtos.AuthResponse;
import eytlyr.com.utp_comercio_backend.auth.dto.AuthDtos.OtpRequest;
import eytlyr.com.utp_comercio_backend.auth.dto.AuthDtos.OtpRequestResponse;
import eytlyr.com.utp_comercio_backend.auth.dto.AuthDtos.OtpVerifyRequest;
import eytlyr.com.utp_comercio_backend.auth.dto.AuthDtos.RefreshRequest;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Acceso", description = "Ingreso con el celular y un código SMS")
public class AuthController {

	private final AuthService auth;

	private final CurrentUser currentUser;

	public AuthController(AuthService auth, CurrentUser currentUser) {
		this.auth = auth;
		this.currentUser = currentUser;
	}

	@PostMapping("/otp/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@SecurityRequirements
	@Operation(summary = "Envía un código SMS al celular (en dev devuelve devCode)")
	public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest body) {
		return auth.requestOtp(body.phone());
	}

	@PostMapping("/otp/verify")
	@SecurityRequirements
	@Operation(summary = "Verifica el código y entrega los tokens. Crea la cuenta si el celular es nuevo")
	public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest body) {
		return auth.verifyOtp(body.phone(), body.code());
	}

	@PostMapping("/refresh")
	@SecurityRequirements
	@Operation(summary = "Rota el token de refresco y entrega un token de acceso nuevo")
	public AuthResponse refresh(@Valid @RequestBody RefreshRequest body) {
		return auth.refresh(body.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Cierra todas las sesiones del usuario")
	public void logout() {
		auth.logout(currentUser.id());
	}
}
