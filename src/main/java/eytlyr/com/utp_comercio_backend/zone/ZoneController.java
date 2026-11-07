package eytlyr.com.utp_comercio_backend.zone;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.user.dto.UserDto;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.MeModeRequest;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneDto;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneModeRequest;

@RestController
@Tag(name = "Zona y modos", description = "NORMAL, PREVENTION (El Niño) y EMERGENCY")
public class ZoneController {

	private final ZoneService zones;

	private final CurrentUser currentUser;

	public ZoneController(ZoneService zones, CurrentUser currentUser) {
		this.zones = zones;
		this.currentUser = currentUser;
	}

	@GetMapping("/api/v1/zones/current")
	@Operation(summary = "Mi zona y su modo actual")
	public ZoneDto current() {
		return zones.current(currentUser.id());
	}

	@PostMapping("/api/v1/admin/zones/{id}/mode")
	@PreAuthorize("hasAnyRole('ADMIN', 'DELEGATE')")
	@Operation(summary = "Municipalidad: cambiar el modo de la zona y avisar a los vecinos")
	public ZoneDto setMode(@PathVariable UUID id, @Valid @RequestBody ZoneModeRequest body) {
		return zones.setMode(id, body);
	}

	@PostMapping("/api/v1/me/mode")
	@Operation(summary = "DISMISS (volver a normal en mi app), ACTIVATE_LOCAL_EMERGENCY o CLEAR_LOCAL")
	public UserDto meMode(@Valid @RequestBody MeModeRequest body) {
		return zones.meMode(currentUser.id(), body.action());
	}
}
