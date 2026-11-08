package eytlyr.com.utp_comercio_backend.user;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.ConflictException;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.user.BrigadeRequest.Status;
import eytlyr.com.utp_comercio_backend.user.dto.RoleDtos.BrigadeRequestDto;
import eytlyr.com.utp_comercio_backend.user.dto.RoleDtos.UserSummaryDto;

/**
 * Roles del barrio: un vecino pide ser brigadista y la Municipalidad lo aprueba o rechaza. El administrador
 * también puede cambiar el rol de un vecino. Los celulares de NEARBY_ADMIN_PHONES / NEARBY_BRIGADE_PHONES siguen
 * recibiendo su rol al ingresar.
 */
@Service
public class RoleService {

	public static final String ALREADY_BRIGADE = "ALREADY_BRIGADE";

	public static final String REQUEST_DECIDED = "BRIGADE_REQUEST_DECIDED";

	public static final String ROLE_NOT_ALLOWED = "ROLE_NOT_ALLOWED";

	/** Roles que se pueden asignar desde la app. ADMIN solo por configuración (NEARBY_ADMIN_PHONES). */
	static final Set<Role> ASSIGNABLE = Set.of(Role.NEIGHBOR, Role.BRIGADIST, Role.DELEGATE);

	private final AppUserRepository users;

	private final BrigadeRequestRepository requests;

	private final Clock clock;

	public RoleService(AppUserRepository users, BrigadeRequestRepository requests, Clock clock) {
		this.users = users;
		this.requests = requests;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- vecino

	/** Pide ser brigadista. Si ya hay una solicitud pendiente, la devuelve (no crea otra). */
	@Transactional
	public BrigadeRequestDto request(UUID userId, String skills) {
		AppUser user = user(userId);
		if (user.getRole() != Role.NEIGHBOR) {
			throw new ConflictException(ALREADY_BRIGADE, "Ya formas parte de la brigada o de la Municipalidad.");
		}
		BrigadeRequest pending = requests.findFirstByUserIdAndStatus(userId, Status.PENDING).orElse(null);
		if (pending != null) {
			return BrigadeRequestDto.from(pending);
		}
		String clean = skills == null || skills.isBlank() ? null : skills.trim();
		return BrigadeRequestDto.from(requests.save(BrigadeRequest.builder().user(user).skills(clean).build()));
	}

	/** Estado de mi última solicitud (NONE si nunca la pedí). */
	@Transactional(readOnly = true)
	public BrigadeRequestDto myRequest(UUID userId) {
		return requests.findFirstByUserIdOrderByCreatedAtDesc(userId).map(BrigadeRequestDto::from)
				.orElse(BrigadeRequestDto.NONE);
	}

	// ---------------------------------------------------------------- Municipalidad

	@Transactional(readOnly = true)
	public List<BrigadeRequestDto> pending() {
		return requests.findTop100ByStatusOrderByCreatedAtAsc(Status.PENDING).stream().map(BrigadeRequestDto::from)
				.toList();
	}

	/** Aprobar convierte al vecino en BRIGADIST; rechazar lo deja como estaba. Solo una vez por solicitud. */
	@Transactional
	public BrigadeRequestDto decide(UUID adminId, UUID requestId, boolean approve) {
		BrigadeRequest r = requests.findById(requestId).orElseThrow(() -> NotFoundException.of("la solicitud"));
		if (r.getStatus() != Status.PENDING) {
			throw new ConflictException(REQUEST_DECIDED, "Esta solicitud ya fue respondida.");
		}
		r.setStatus(approve ? Status.APPROVED : Status.REJECTED);
		r.setDecidedBy(adminId);
		r.setDecidedAt(OffsetDateTime.now(clock));
		if (approve && r.getUser().getRole() == Role.NEIGHBOR) {
			r.getUser().setRole(Role.BRIGADIST);
		}
		return BrigadeRequestDto.from(r);
	}

	@Transactional(readOnly = true)
	public UserSummaryDto findByPhone(String phone) {
		String digits = phone == null ? "" : phone.replaceAll("\\D", "");
		if (digits.length() > 9) {
			digits = digits.substring(digits.length() - 9);
		}
		return users.findByPhone(digits).map(UserSummaryDto::from)
				.orElseThrow(() -> NotFoundException.of("un vecino con ese celular"));
	}

	/** El administrador cambia el rol de otro vecino (nunca el propio, y nunca a ADMIN desde la app). */
	@Transactional
	public UserSummaryDto changeRole(UUID adminId, UUID userId, Role role) {
		if (!ASSIGNABLE.contains(role)) {
			throw new BusinessRuleException(ROLE_NOT_ALLOWED, "Solo puedes asignar Vecino, Brigadista o Delegado.");
		}
		if (adminId.equals(userId)) {
			throw new BusinessRuleException(ROLE_NOT_ALLOWED, "No puedes cambiar tu propio rol.");
		}
		AppUser user = user(userId);
		if (user.getRole() == Role.ADMIN) {
			throw new BusinessRuleException(ROLE_NOT_ALLOWED, "El rol de la Municipalidad se configura en el servidor.");
		}
		user.setRole(role);
		return UserSummaryDto.from(user);
	}

	private AppUser user(UUID id) {
		return users.findById(id).orElseThrow(() -> NotFoundException.of("el usuario"));
	}
}
