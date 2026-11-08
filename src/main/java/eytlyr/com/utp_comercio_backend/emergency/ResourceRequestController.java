package eytlyr.com.utp_comercio_backend.emergency;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.emergency.dto.ResourceAvailabilityDtos.AvailabilityPingResponseDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.ResourceAvailabilityDtos.CandidatePingDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.ResourceAvailabilityDtos.CreateAvailabilityPingRequest;

@RestController
@Tag(name = "Ping de Disponibilidad de Recursos", description = "Flujo de ping y reserva de recursos entre vecinos")
public class ResourceRequestController {

	private final ResourceAvailabilityPingService pingService;
	private final CurrentUser currentUser;

	public ResourceRequestController(ResourceAvailabilityPingService pingService, CurrentUser currentUser) {
		this.pingService = pingService;
		this.currentUser = currentUser;
	}

	@PostMapping("/api/v1/resources/requests")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Enviar ping de disponibilidad a los 3 proveedores más cercanos de un recurso")
	public AvailabilityPingResponseDto createPing(@Valid @RequestBody CreateAvailabilityPingRequest body) {
		return pingService.createPing(currentUser.id(), body);
	}

	@PostMapping("/api/v1/resources/requests/{id}/accept")
	@Operation(summary = "El primer proveedor en responder acepta prestar el recurso; cancela a los demás")
	public AvailabilityPingResponseDto accept(@PathVariable UUID id) {
		return pingService.acceptRequest(currentUser.id(), id);
	}

	@GetMapping("/api/v1/resources/requests/pending")
	@Operation(summary = "Solicitudes pendientes de disponibilidad dirigidas a mí (se ocultan si otro vecino aceptó)")
	public List<CandidatePingDto> pending() {
		return pingService.getPendingPingsForProvider(currentUser.id());
	}

	@GetMapping("/api/v1/resources/requests/{id}")
	@Operation(summary = "Detalle de una solicitud de recurso")
	public AvailabilityPingResponseDto getById(@PathVariable UUID id) {
		return pingService.getRequest(id);
	}

}

