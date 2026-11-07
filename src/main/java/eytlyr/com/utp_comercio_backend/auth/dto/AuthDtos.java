package eytlyr.com.utp_comercio_backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import eytlyr.com.utp_comercio_backend.user.dto.UserDto;

/** Peticiones y respuestas de /api/v1/auth. */
public final class AuthDtos {

	public static final String PHONE_REGEX = "^9\\d{8}$";

	public static final String PHONE_MESSAGE = "Los celulares en Perú tienen 9 dígitos y empiezan con 9";

	private AuthDtos() {
	}

	public record OtpRequest(
			@NotBlank(message = PHONE_MESSAGE) @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone) {
	}

	/** devCode solo aparece en el perfil dev. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OtpRequestResponse(long expiresInSeconds, long resendInSeconds, String devCode) {
	}

	public record OtpVerifyRequest(
			@NotBlank(message = PHONE_MESSAGE) @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
			@NotBlank(message = "Ingresa el código que te llegó por SMS")
			@Pattern(regexp = "^\\d{4,8}$", message = "El código solo tiene números") String code) {
	}

	public record RefreshRequest(@NotBlank(message = "Falta el token de refresco") String refreshToken) {
	}

	public record AuthResponse(String accessToken, String refreshToken, long expiresIn,
			@JsonProperty("isNewUser") boolean isNewUser, UserDto user) {
	}
}
