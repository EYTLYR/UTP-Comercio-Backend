package eytlyr.com.utp_comercio_backend.municipality;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.municipality.dto.MunicipalityDtos.MunicipalityDto;
import eytlyr.com.utp_comercio_backend.municipality.dto.MunicipalityDtos.MunicipalityRegistration;
import eytlyr.com.utp_comercio_backend.municipality.dto.MunicipalityDtos.RejectRequest;
import eytlyr.com.utp_comercio_backend.municipality.dto.MunicipalityDtos.StaffDto;
import eytlyr.com.utp_comercio_backend.municipality.dto.MunicipalityDtos.StaffRequest;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneCreateRequest;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneDto;

/** Registro de Municipalidades, aprobación por la plataforma y panel de cada Municipalidad. */
@RestController
@Tag(name = "Municipalidades")
public class MunicipalityController {

	private final MunicipalityService service;

	private final CurrentUser currentUser;

	public MunicipalityController(MunicipalityService service, CurrentUser currentUser) {
		this.service = service;
		this.currentUser = currentUser;
	}

	// ---------------------------------------------------------------- registro (cualquier usuario con sesión)

	@PostMapping("/api/v1/municipalities")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrar mi Municipalidad (queda pendiente hasta que la plataforma la apruebe)")
	public MunicipalityDto register(@Valid @RequestBody MunicipalityRegistration body) {
		return service.register(currentUser.id(), body);
	}

	@GetMapping("/api/v1/municipalities/mine")
	@Operation(summary = "Estado de mi registro de Municipalidad (NONE, PENDING, APPROVED o REJECTED)")
	public MunicipalityDto mine() {
		return service.mine(currentUser.id());
	}

	@GetMapping("/api/v1/municipalities/districts")
	@Operation(summary = "Distritos que ya tienen zonas en Nearby")
	public List<String> districts() {
		return service.districts();
	}

	// ---------------------------------------------------------------- administrador de la plataforma

	@GetMapping("/api/v1/admin/municipalities")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Municipalidades registradas, las pendientes primero")
	public List<MunicipalityDto> all() {
		return service.all();
	}

	@PostMapping("/api/v1/admin/municipalities/{id}/approve")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Aprobar: quien la registró pasa a MUNICIPALITY (409 si ya se respondió)")
	public MunicipalityDto approve(@PathVariable UUID id) {
		return service.decide(currentUser.id(), id, true, null);
	}

	@PostMapping("/api/v1/admin/municipalities/{id}/reject")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Rechazar con un motivo (409 si ya se respondió)")
	public MunicipalityDto reject(@PathVariable UUID id, @Valid @RequestBody(required = false) RejectRequest body) {
		return service.decide(currentUser.id(), id, false, body != null ? body.reason() : null);
	}

	// ---------------------------------------------------------------- panel de la Municipalidad

	@GetMapping("/api/v1/municipality/zones")
	@PreAuthorize("hasAnyRole('ADMIN', 'MUNICIPALITY', 'DELEGATE')")
	@Operation(summary = "Zonas que administro (Municipalidad: su distrito; delegado: su zona; plataforma: todas)")
	public List<ZoneDto> zones() {
		return service.zones(currentUser.id());
	}

	@PostMapping("/api/v1/municipality/zones")
	@PreAuthorize("hasAnyRole('ADMIN', 'MUNICIPALITY')")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Crear una zona (Municipalidad: en su distrito; ADMIN: indica el distrito)")
	public ZoneDto createZone(@Valid @RequestBody ZoneCreateRequest body) {
		return service.createZone(currentUser.id(), body);
	}

	@GetMapping("/api/v1/municipality/staff")
	@PreAuthorize("hasRole('MUNICIPALITY')")
	@Operation(summary = "Equipo de mi Municipalidad")
	public List<StaffDto> staff() {
		return service.staff(currentUser.id());
	}

	@PostMapping("/api/v1/municipality/staff")
	@PreAuthorize("hasRole('MUNICIPALITY')")
	@Operation(summary = "Agregar a un funcionario por celular (debe haber ingresado a Nearby)")
	public StaffDto addStaff(@Valid @RequestBody StaffRequest body) {
		return service.addStaff(currentUser.id(), body.phone());
	}

	@DeleteMapping("/api/v1/municipality/staff/{userId}")
	@PreAuthorize("hasRole('MUNICIPALITY')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Quitar a un funcionario del equipo (vuelve a ser vecino)")
	public void removeStaff(@PathVariable UUID userId) {
		service.removeStaff(currentUser.id(), userId);
	}
}
