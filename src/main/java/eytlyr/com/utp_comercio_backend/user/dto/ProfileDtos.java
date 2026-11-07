package eytlyr.com.utp_comercio_backend.user.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.ai.CapabilityTag;
import eytlyr.com.utp_comercio_backend.ai.SourceType;
import eytlyr.com.utp_comercio_backend.auth.dto.AuthDtos;
import eytlyr.com.utp_comercio_backend.user.LivesWith;
import eytlyr.com.utp_comercio_backend.user.PrepItem;

/** Peticiones y respuestas de /api/v1/me. */
public final class ProfileDtos {

	private ProfileDtos() {
	}

	public record UpdateNameRequest(
			@NotBlank(message = "Escribe tu nombre")
			@Size(min = 2, max = 60, message = "El nombre debe tener entre 2 y 60 caracteres") String name) {
	}

	public record LocationRequest(
			@DecimalMin("-90") @DecimalMax("90") Double lat,
			@DecimalMin("-180") @DecimalMax("180") Double lng,
			@Size(max = 200, message = "La dirección es muy larga") String addressText) {
	}

	public record UserLocationDto(Double lat, Double lng, String addressText, UUID zoneId, String zoneName) {
	}

	public record TrustedContactDto(
			@NotBlank(message = "Escribe el nombre del contacto") @Size(max = 120) String name,
			@NotBlank(message = AuthDtos.PHONE_MESSAGE)
			@Pattern(regexp = AuthDtos.PHONE_REGEX, message = AuthDtos.PHONE_MESSAGE) String phone,
			@Min(value = 1, message = "La posición va de 1 a 3")
			@Max(value = 3, message = "La posición va de 1 a 3") Integer position) {
	}

	public record MedicalCardDto(
			@Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Grupo sanguíneo inválido (ejemplo: O+)") String bloodType,
			@Size(max = 300, message = "Las alergias no deben pasar de 300 caracteres") String allergies,
			@Size(max = 300, message = "Las condiciones no deben pasar de 300 caracteres") String conditions,
			List<LivesWith> livesWith) {
	}

	/** Ficha médica que ve la brigada durante un SOS o un reporte abierto. */
	public record BrigadeMedicalCardDto(UUID userId, String name, String phone, MedicalCardDto medicalCard) {
	}

	public record DeviceTokenRequest(
			@NotBlank(message = "Falta el token del dispositivo") @Size(max = 300) String token,
			@NotBlank(message = "Falta la plataforma")
			@Pattern(regexp = "^(ANDROID|IOS|WEB)$", message = "La plataforma debe ser ANDROID, IOS o WEB") String platform) {
	}

	public record SettingsRequest(@NotNull(message = "Indica si aceptas que la IA aprenda de tus publicaciones")
			Boolean aiConsent) {
	}

	public record AiLearningDto(UUID id, CapabilityTag tag, String label, String evidenceText, SourceType sourceType) {
	}

	public record PrepChecklistItemDto(@NotNull(message = "Falta el ítem") PrepItem itemKey, boolean done) {
	}
}
