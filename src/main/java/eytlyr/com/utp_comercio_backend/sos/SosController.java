package eytlyr.com.utp_comercio_backend.sos;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.CheckInDto;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.CheckInRequest;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.NearbySosDto;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.SosCreatedDto;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.SosDetailDto;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.SosRequest;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.SosResponseRequest;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.SosTypeRequest;

@RestController
@Tag(name = "SOS", description = "Botón de emergencia, respuestas de vecinos y check-ins")
public class SosController {

	private final SosService sos;

	private final CurrentUser currentUser;

	public SosController(SosService sos, CurrentUser currentUser) {
		this.sos = sos;
		this.currentUser = currentUser;
	}

	@PostMapping("/api/v1/sos")
	@Operation(summary = "Enviar SOS (idempotente por clientId). Avisa a vecinos a 500 m, brigada y contactos")
	public ResponseEntity<SosCreatedDto> create(@Valid @RequestBody SosRequest body) {
		return sos.create(currentUser.id(), body).toResponse();
	}

	@PatchMapping("/api/v1/sos/{id}")
	@Operation(summary = "Elegir el tipo de emergencia (recalcula el código SMS)")
	public SosDetailDto changeType(@PathVariable UUID id, @Valid @RequestBody SosTypeRequest body) {
		return sos.changeType(currentUser.id(), id, body.type());
	}

	@PostMapping("/api/v1/sos/{id}/safe")
	@Operation(summary = "Estoy a salvo: cierra el SOS y avisa a mis contactos")
	public SosDetailDto safe(@PathVariable UUID id) {
		return sos.markSafe(currentUser.id(), id);
	}

	@PostMapping("/api/v1/sos/{id}/cancel")
	@Operation(summary = "Cancelar el SOS (no envía nada más)")
	public SosDetailDto cancel(@PathVariable UUID id) {
		return sos.cancel(currentUser.id(), id);
	}

	@GetMapping("/api/v1/sos/{id}")
	@Operation(summary = "Detalle del SOS con los vecinos que responden")
	public SosDetailDto get(@PathVariable UUID id) {
		return sos.get(id);
	}

	@GetMapping("/api/v1/sos/nearby")
	@Operation(summary = "SOS activos cerca de mí (ubicación aproximada)")
	public List<NearbySosDto> nearby(@RequestParam(defaultValue = "1000") int radiusM) {
		int safeRadius = Math.clamp(radiusM, 100, 5000);
		List<NearbySosDto> list = sos.nearby(currentUser.id(), safeRadius);
		if (list.size() > 50) {
			return list.subList(0, 50);
		}
		return list;
	}

	@PostMapping("/api/v1/sos/{id}/responses")
	@Operation(summary = "Responder: GOING (voy), DECLINED (no puedo) o ARRIVED (llegué)")
	public SosDetailDto respond(@PathVariable UUID id, @Valid @RequestBody SosResponseRequest body) {
		return sos.respond(currentUser.id(), id, body.status());
	}

	@PostMapping("/api/v1/check-ins")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Estoy bien / Necesito ayuda (SAFE avisa a mis contactos)")
	public CheckInDto checkIn(@Valid @RequestBody CheckInRequest body) {
		return sos.checkIn(currentUser.id(), body.status());
	}
}
