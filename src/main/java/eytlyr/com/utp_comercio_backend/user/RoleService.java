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
import eytlyr.com.utp_comercio_backend.common.ForbiddenException;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.municipality.Jurisdiction;
import eytlyr.com.utp_comercio_backend.user.BrigadeRequest.Status;
import eytlyr.com.utp_comercio_backend.user.dto.RoleDtos.BrigadeRequestDto;
import eytlyr.com.utp_comercio_backend.user.dto.RoleDtos.UserSummaryDto;

/**
 * Roles del barrio: un vecino pide ser brigadista y la Municipalidad de su distrito (o el delegado de su zona) lo
 * aprueba o rechaza. La Municipalidad también puede cambiar el rol de los vecinos de su distrito. Cada quien solo
 * ve y decide sobre su jurisdicción ({@link Jurisdiction}); el administrador de la plataforma, sobre todo.
 */
@Service
public class RoleService {

	public static final String ALREADY_BRIGADE = "ALREADY_BRIGADE";

	public static final String REQUEST_DECIDED = "BRIGADE_REQUEST_DECIDED";

	public static final String ROLE_NOT_ALLOWED = "ROLE_NOT_ALLOWED";

	public static final String OUT_OF_JURISDICTION = "OUT_OF_JURISDICTION";

	/** Roles que se asignan a vecinos. MUNICIPALITY se da al aprobar o en «Mi equipo»; ADMIN solo por configuración. */
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

	/** Pendientes de mi jurisdicción. */
	@Transactional(readOnly = true)
	public List<BrigadeRequestDto> pending(UUID actorId) {
		AppUser actor = user(actorId);
		return requests.findTop100ByStatusOrderByCreatedAtAsc(Status.PENDING).stream()
				.filter(r -> Jurisdiction.covers(actor, r.getUser().getZone()))
				.map(BrigadeRequestDto::from)
				.toList();
	}

	/** Aprobar convierte al vecino en BRIGADIST; rechazar lo deja como estaba. Solo una vez por solicitud. */
	@Transactional
	public BrigadeRequestDto decide(UUID adminId, UUID requestId, boolean approve) {
		BrigadeRequest r = requests.findById(requestId).orElseThrow(() -> NotFoundException.of("la solicitud"));
		requireJurisdiction(user(adminId), r.getUser());
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

	/** La Municipalidad cambia el rol de un vecino de su distrito (nunca el propio, ni a otra Municipalidad). */
	@Transactional
	public UserSummaryDto changeRole(UUID adminId, UUID userId, Role role) {
		if (!ASSIGNABLE.contains(role)) {
			throw new BusinessRuleException(ROLE_NOT_ALLOWED, "Solo puedes asignar Vecino, Brigadista o Delegado.");
		}
		if (adminId.equals(userId)) {
			throw new BusinessRuleException(ROLE_NOT_ALLOWED, "No puedes cambiar tu propio rol.");
		}
		AppUser user = user(userId);
		if (user.getRole() == Role.ADMIN || user.getRole() == Role.MUNICIPALITY) {
			throw new BusinessRuleException(ROLE_NOT_ALLOWED,
					"El equipo de una Municipalidad se administra en «Mi equipo».");
		}
		requireJurisdiction(user(adminId), user);
		user.setRole(role);
		return UserSummaryDto.from(user);
	}

	private static void requireJurisdiction(AppUser actor, AppUser neighbor) {
		if (!Jurisdiction.covers(actor, neighbor.getZone())) {
			throw new ForbiddenException(OUT_OF_JURISDICTION, "Este vecino no vive en tu distrito o zona.");
		}
	}

	private AppUser user(UUID id) {
		return users.findById(id).orElseThrow(() -> NotFoundException.of("el usuario"));
	}
}
