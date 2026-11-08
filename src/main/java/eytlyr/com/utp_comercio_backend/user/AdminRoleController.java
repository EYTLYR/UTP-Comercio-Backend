package eytlyr.com.utp_comercio_backend.user;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.user.dto.RoleDtos.BrigadeRequestDto;
import eytlyr.com.utp_comercio_backend.user.dto.RoleDtos.RoleChangeRequest;
import eytlyr.com.utp_comercio_backend.user.dto.RoleDtos.UserSummaryDto;

/** Panel de la Municipalidad: solicitudes de brigadista y roles, siempre dentro de su jurisdicción. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Municipalidad")
@PreAuthorize("hasAnyRole('ADMIN', 'MUNICIPALITY', 'DELEGATE')")
public class AdminRoleController {

	private final RoleService roles;

	private final CurrentUser currentUser;

	public AdminRoleController(RoleService roles, CurrentUser currentUser) {
		this.roles = roles;
		this.currentUser = currentUser;
	}

	@GetMapping("/brigade-requests")
	@Operation(summary = "Solicitudes de brigadista pendientes, de la más antigua a la más nueva")
	public List<BrigadeRequestDto> pending() {
		return roles.pending(currentUser.id());
	}

	@PostMapping("/brigade-requests/{id}/approve")
	@Operation(summary = "Aprobar: el vecino pasa a BRIGADIST (409 si ya se respondió)")
	public BrigadeRequestDto approve(@PathVariable UUID id) {
		return roles.decide(currentUser.id(), id, true);
	}

	@PostMapping("/brigade-requests/{id}/reject")
	@Operation(summary = "Rechazar la solicitud (409 si ya se respondió)")
	public BrigadeRequestDto reject(@PathVariable UUID id) {
		return roles.decide(currentUser.id(), id, false);
	}

	@GetMapping("/users")
	@Operation(summary = "Buscar un vecino por su celular")
	public UserSummaryDto findByPhone(@RequestParam String phone) {
		return roles.findByPhone(phone);
	}

	@PutMapping("/users/{id}/role")
	@PreAuthorize("hasAnyRole('ADMIN', 'MUNICIPALITY')")
	@Operation(summary = "Cambiar el rol de un vecino de mi distrito: NEIGHBOR, BRIGADIST o DELEGATE")
	public UserSummaryDto changeRole(@PathVariable UUID id, @Valid @RequestBody RoleChangeRequest body) {
		return roles.changeRole(currentUser.id(), id, body.role());
	}
}
