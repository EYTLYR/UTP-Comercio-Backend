package eytlyr.com.utp_comercio_backend.user;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.BrigadeMedicalCardDto;

@RestController
@RequestMapping("/api/v1/brigade")
@Tag(name = "Brigada")
@PreAuthorize("hasAnyRole('BRIGADIST', 'MUNICIPALITY', 'ADMIN')")
public class BrigadeController {

	private final ProfileService profile;

	public BrigadeController(ProfileService profile) {
		this.profile = profile;
	}

	@GetMapping("/medical-cards/{userId}")
	@Operation(summary = "Ficha médica de una persona con SOS activo o reporte abierto (403 si no)")
	public BrigadeMedicalCardDto medicalCard(@PathVariable UUID userId) {
		return profile.medicalCardForBrigade(userId);
	}
}
