package eytlyr.com.utp_comercio_backend.emergency;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.common.ConflictException;
import eytlyr.com.utp_comercio_backend.common.PageResponse;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.DashboardDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ReportStatusRequest;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceRequest;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceSummaryDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.SafePointDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.SupportGroupDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.VulnerableReportDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.VulnerableReportRequest;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneDto;

@RestController
@Tag(name = "Emergencia", description = "Tablero humanitario, recursos críticos, grupos de apoyo y puntos seguros")
public class EmergencyController {

	private final EmergencyService emergency;

	private final CurrentUser currentUser;

	public EmergencyController(EmergencyService emergency, CurrentUser currentUser) {
		this.emergency = emergency;
		this.currentUser = currentUser;
	}

	@GetMapping("/api/v1/zones/{id}/dashboard")
	@Operation(summary = "Resumen del tablero humanitario de la zona")
	public DashboardDto dashboard(@PathVariable UUID id) {
		return emergency.dashboard(id);
	}

	@GetMapping("/api/v1/vulnerable-reports")
	@Operation(summary = "Reportes de vecinos vulnerables (dirección exacta solo para la brigada)")
	public PageResponse<VulnerableReportDto> reports(@RequestParam(required = false) ReportStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.clamp(size, 1, 50);
		return emergency.reports(currentUser.id(), status, safePage, safeSize);
	}

	@PostMapping("/api/v1/vulnerable-reports")
	@Operation(summary = "Reportar a un vecino vulnerable (idempotente por clientId)")
	public ResponseEntity<VulnerableReportDto> createReport(@Valid @RequestBody VulnerableReportRequest body) {
		return emergency.createReport(currentUser.id(), body).toResponse();
	}

	@PostMapping("/api/v1/vulnerable-reports/{id}/assign")
	@Operation(summary = "Voy yo: queda en camino y asignado a mí")
	public VulnerableReportDto assign(@PathVariable UUID id) {
		return emergency.assign(currentUser.id(), id);
	}

	@PatchMapping("/api/v1/vulnerable-reports/{id}/status")
	@Operation(summary = "Cambiar el estado (quien está asignado o la brigada)")
	public VulnerableReportDto status(@PathVariable UUID id, @Valid @RequestBody ReportStatusRequest body) {
		return emergency.updateStatus(currentUser.id(), id, body.status());
	}

	@GetMapping("/api/v1/resources/summary")
	@Operation(summary = "Tengo / necesito por tipo de recurso crítico")
	public List<ResourceSummaryDto> resourceSummary() {
		return emergency.resourceSummary(currentUser.id());
	}

	@GetMapping("/api/v1/resources")
	@Operation(summary = "Listados de recursos críticos")
	public List<ResourceDto> resources(@RequestParam(required = false) ResourceType type,
			@RequestParam(required = false) ListingKind kind) {
		return emergency.resources(currentUser.id(), type, kind);
	}

	@PostMapping("/api/v1/resources")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Publicar que tengo o necesito un recurso")
	public ResourceDto createResource(@Valid @RequestBody ResourceRequest body) {
		return emergency.createResource(currentUser.id(), body);
	}

	@GetMapping("/api/v1/support-groups")
	@Operation(summary = "Grupos de apoyo de la zona")
	public List<SupportGroupDto> groups() {
		return emergency.groups(currentUser.id());
	}

	@PostMapping("/api/v1/support-groups/{id}/members")
	@Operation(summary = "Unirme al grupo")
	public SupportGroupDto join(@PathVariable UUID id) {
		return emergency.join(currentUser.id(), id);
	}

	@DeleteMapping("/api/v1/support-groups/{id}/members/me")
	@Operation(summary = "Salir del grupo")
	public SupportGroupDto leave(@PathVariable UUID id) {
		return emergency.leave(currentUser.id(), id);
	}

	@GetMapping("/api/v1/safe-points")
	@Operation(summary = "Puntos de reunión y refugios")
	public List<SafePointDto> safePoints() {
		return emergency.safePoints(currentUser.id());
	}

	@PostMapping("/api/v1/emergency/validate-zone/{id}")
	@PreAuthorize("hasAnyRole('BRIGADIST', 'DELEGATE', 'MUNICIPALITY', 'ADMIN')")
	@Operation(summary = "Validación manual de brigadista: valida la alerta y activa el modo emergencia en la zona")
	public ZoneDto validateZone(@PathVariable UUID id, @RequestParam(required = false) String reason) {
		return emergency.validateAndActivateEmergency(currentUser.id(), id, reason);
	}

	@PostMapping("/api/v1/safe-points/{id}/reserve")
	@Operation(summary = "Reservar cupos en un albergue (concurrencia segura: 409 Conflict si está lleno)")
	public ResponseEntity<SafePointDto> reserveSpot(@PathVariable UUID id,
			@RequestParam(defaultValue = "1") int count) {
		try {
			return ResponseEntity.ok(emergency.reserveSpot(currentUser.id(), id, count));
		}
		catch (ObjectOptimisticLockingFailureException ex) {
			throw new ConflictException("SHELTER_FULL",
					"Refugio lleno. Se ocuparon los últimos lugares mientras procesábamos tu solicitud");
		}
	}
}
