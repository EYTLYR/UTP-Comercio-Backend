package eytlyr.com.utp_comercio_backend.user;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.AiLearningDto;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.DeviceTokenRequest;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.LocationRequest;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.MedicalCardDto;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.PrepChecklistItemDto;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.SettingsRequest;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.TrustedContactDto;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.UpdateNameRequest;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.UserLocationDto;
import eytlyr.com.utp_comercio_backend.user.dto.UserDto;

/** Perfil propio: siempre del usuario del token, nunca de otro. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Mi perfil")
public class MeController {

	private final UserService users;

	private final ProfileService profile;

	private final CurrentUser currentUser;

	public MeController(UserService users, ProfileService profile, CurrentUser currentUser) {
		this.users = users;
		this.profile = profile;
		this.currentUser = currentUser;
	}

	@GetMapping
	@Operation(summary = "Mi perfil y el modo que la app debe mostrar (effectiveMode)")
	public UserDto me() {
		return users.me(currentUser.id());
	}

	@PutMapping
	@Operation(summary = "Cambiar mi nombre (2 a 60 caracteres)")
	public UserDto updateName(@Valid @RequestBody UpdateNameRequest body) {
		return profile.updateName(currentUser.id(), body.name());
	}

	@GetMapping("/location")
	@Operation(summary = "Mi ubicación y dirección privada")
	public UserLocationDto location() {
		return profile.userLocation(currentUser.id());
	}

	@PutMapping("/location")
	@Operation(summary = "Guardar mi ubicación; asigna la zona más cercana (422 si Nearby no llega)")
	public UserDto updateLocation(@Valid @RequestBody LocationRequest body) {
		return profile.updateLocation(currentUser.id(), body);
	}

	@GetMapping("/trusted-contacts")
	@Operation(summary = "Mis contactos de confianza (hasta 3)")
	public List<TrustedContactDto> trustedContacts() {
		return profile.trustedContacts(currentUser.id());
	}

	@PutMapping("/trusted-contacts")
	@Operation(summary = "Reemplazar mis contactos de confianza (máximo 3)")
	public List<TrustedContactDto> replaceTrustedContacts(@Valid @RequestBody List<@Valid TrustedContactDto> body) {
		return profile.replaceTrustedContacts(currentUser.id(), body);
	}

	@GetMapping("/medical-card")
	@Operation(summary = "Mi ficha médica (solo yo la veo; la brigada solo durante un SOS)")
	public MedicalCardDto medicalCard() {
		return profile.medicalCard(currentUser.id());
	}

	@PutMapping("/medical-card")
	@Operation(summary = "Guardar mi ficha médica")
	public MedicalCardDto updateMedicalCard(@Valid @RequestBody MedicalCardDto body) {
		return profile.updateMedicalCard(currentUser.id(), body);
	}

	@PutMapping("/device-token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Registrar el token de notificaciones del teléfono")
	public void deviceToken(@Valid @RequestBody DeviceTokenRequest body) {
		profile.registerDeviceToken(currentUser.id(), body);
	}

	@PutMapping("/settings")
	@Operation(summary = "Consentimiento de IA; si es false se borra lo aprendido")
	public UserDto settings(@Valid @RequestBody SettingsRequest body) {
		return profile.updateSettings(currentUser.id(), body.aiConsent());
	}

	@GetMapping("/ai-learnings")
	@Operation(summary = "Lo que la IA aprendió de mí (Protocolo Lázaro)")
	public List<AiLearningDto> aiLearnings() {
		return profile.aiLearnings(currentUser.id());
	}

	@DeleteMapping("/ai-learnings/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Borrar un aprendizaje de la IA (no se vuelve a crear)")
	public void deleteAiLearning(@PathVariable UUID id) {
		profile.deleteAiLearning(currentUser.id(), id);
	}

	@GetMapping("/prep-checklist")
	@Operation(summary = "Lista \"Prepara tu casa\" (El Niño)")
	public List<PrepChecklistItemDto> prepChecklist() {
		return profile.prepChecklist(currentUser.id());
	}

	@PutMapping("/prep-checklist")
	@Operation(summary = "Marcar ítems de \"Prepara tu casa\"")
	public List<PrepChecklistItemDto> updatePrepChecklist(@Valid @RequestBody List<@Valid PrepChecklistItemDto> body) {
		return profile.updatePrepChecklist(currentUser.id(), body);
	}
}
