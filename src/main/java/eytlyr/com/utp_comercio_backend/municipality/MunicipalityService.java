package eytlyr.com.utp_comercio_backend.municipality;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.ConflictException;
import eytlyr.com.utp_comercio_backend.common.ForbiddenException;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.municipality.Municipality.Status;
import eytlyr.com.utp_comercio_backend.municipality.dto.MunicipalityDtos.MunicipalityDto;
import eytlyr.com.utp_comercio_backend.municipality.dto.MunicipalityDtos.MunicipalityRegistration;
import eytlyr.com.utp_comercio_backend.municipality.dto.MunicipalityDtos.StaffDto;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.user.Role;
import eytlyr.com.utp_comercio_backend.zone.ZoneFactory;
import eytlyr.com.utp_comercio_backend.zone.ZoneRepository;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneCreateRequest;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneDto;

/**
 * Municipalidades: un funcionario registra la suya desde la app, el administrador de la plataforma la aprueba
 * y desde ahí la Municipalidad maneja su distrito y su equipo. Así los roles no dependen de listas fijas.
 */
@Service
public class MunicipalityService {

	public static final String ALREADY_STAFF = "ALREADY_STAFF";

	public static final String DUPLICATE_RUC = "MUNICIPALITY_DUPLICATE_RUC";

	public static final String DUPLICATE_DISTRICT = "MUNICIPALITY_DUPLICATE_DISTRICT";

	public static final String DECIDED = "MUNICIPALITY_DECIDED";

	public static final String NOT_STAFF = "NOT_MUNICIPALITY_STAFF";

	private final MunicipalityRepository municipalities;

	private final AppUserRepository users;

	private final ZoneRepository zones;

	private final Clock clock;

	private final ZoneFactory zoneFactory;

	public MunicipalityService(MunicipalityRepository municipalities, AppUserRepository users, ZoneRepository zones,
			Clock clock, ZoneFactory zoneFactory) {
		this.zoneFactory = zoneFactory;
		this.municipalities = municipalities;
		this.users = users;
		this.zones = zones;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- registro

	/** Registra la Municipalidad (queda PENDING). Si ya hay una pendiente de esta persona, la devuelve. */
	@Transactional
	public MunicipalityDto register(UUID userId, MunicipalityRegistration req) {
		AppUser user = user(userId);
		if (user.getRole() == Role.ADMIN || user.getRole() == Role.MUNICIPALITY) {
			throw new ConflictException(ALREADY_STAFF, "Ya administras una Municipalidad en Nearby.");
		}
		Municipality pending = municipalities.findFirstByRequestedByIdOrderByCreatedAtDesc(userId)
				.filter(m -> m.getStatus() == Status.PENDING).orElse(null);
		if (pending != null) {
			return MunicipalityDto.from(pending);
		}
		String ruc = req.ruc().trim();
		String districtKey = Jurisdiction.key(req.district());
		if (municipalities.existsByRucAndStatusNot(ruc, Status.REJECTED)) {
			throw new ConflictException(DUPLICATE_RUC, "Ese RUC ya está registrado en Nearby.");
		}
		if (municipalities.existsByDistrictKeyAndStatusNot(districtKey, Status.REJECTED)) {
			throw new ConflictException(DUPLICATE_DISTRICT,
					"Ese distrito ya tiene una Municipalidad registrada. Pide a su equipo que te agregue.");
		}
		Municipality m = Municipality.builder()
				.name(req.name().trim())
				.district(req.district().trim().replaceAll("\\s+", " "))
				.districtKey(districtKey)
				.ruc(ruc)
				.contactName(req.contactName().trim())
				.contactPosition(blankToNull(req.contactPosition()))
				.email(req.email().trim().toLowerCase(Locale.ROOT))
				.requestedBy(user)
				.build();
		return MunicipalityDto.from(municipalities.save(m));
	}

	/** Mi último registro (NONE si nunca registré una). */
	@Transactional(readOnly = true)
	public MunicipalityDto mine(UUID userId) {
		AppUser user = user(userId);
		if (user.getMunicipality() != null) {
			return MunicipalityDto.from(user.getMunicipality());
		}
		return municipalities.findFirstByRequestedByIdOrderByCreatedAtDesc(userId).map(MunicipalityDto::from)
				.orElse(MunicipalityDto.NONE);
	}

	/** Distritos que ya tienen zonas en Nearby (sugerencias del formulario). */
	@Transactional(readOnly = true)
	public List<String> districts() {
		TreeMap<String, String> byKey = new TreeMap<>();
		zones.findAll().forEach(z -> byKey.putIfAbsent(Jurisdiction.key(z.getDistrict()), z.getDistrict()));
		return List.copyOf(byKey.values());
	}

	// ---------------------------------------------------------------- administrador de la plataforma

	/** Todas, las pendientes primero (de la más antigua a la más nueva). */
	@Transactional(readOnly = true)
	public List<MunicipalityDto> all() {
		return municipalities.findTop200ByOrderByCreatedAtDesc().stream()
				.sorted(Comparator.comparing((Municipality m) -> m.getStatus() != Status.PENDING)
						.thenComparing(m -> m.getStatus() == Status.PENDING ? m.getCreatedAt() : null,
								Comparator.nullsLast(Comparator.naturalOrder())))
				.map(MunicipalityDto::from)
				.toList();
	}

	/** Aprobar: quien la registró pasa a MUNICIPALITY. Rechazar guarda el motivo. Solo una vez. */
	@Transactional
	public MunicipalityDto decide(UUID adminId, UUID id, boolean approve, String reason) {
		Municipality m = municipalities.findById(id).orElseThrow(() -> NotFoundException.of("la Municipalidad"));
		if (m.getStatus() != Status.PENDING) {
			throw new ConflictException(DECIDED, "Este registro ya fue respondido.");
		}
		m.setStatus(approve ? Status.APPROVED : Status.REJECTED);
		m.setDecidedBy(adminId);
		m.setDecidedAt(OffsetDateTime.now(clock));
		if (approve) {
			AppUser by = m.getRequestedBy();
			if (by.getRole() != Role.ADMIN) {
				by.setRole(Role.MUNICIPALITY);
				by.setMunicipality(m);
			}
		}
		else {
			m.setRejectReason(blankToNull(reason));
		}
		return MunicipalityDto.from(m);
	}

	// ---------------------------------------------------------------- equipo de la Municipalidad

	@Transactional(readOnly = true)
	public List<StaffDto> staff(UUID actorId) {
		Municipality m = ownMunicipality(actorId);
		return users.findByMunicipalityIdOrderByName(m.getId()).stream()
				.map(u -> new StaffDto(u.getId(), u.getName(), u.getPhone(), u.getPhotoUrl(), u.getId().equals(actorId)))
				.toList();
	}

	/** Agrega a un funcionario por celular. Debe haber ingresado a Nearby al menos una vez. */
	@Transactional
	public StaffDto addStaff(UUID actorId, String phone) {
		Municipality m = ownMunicipality(actorId);
		String digits = phone == null ? "" : phone.replaceAll("\\D", "");
		if (digits.length() > 9) {
			digits = digits.substring(digits.length() - 9);
		}
		AppUser u = users.findByPhone(digits)
				.orElseThrow(() -> NotFoundException.of("a nadie con ese celular. Pídele que ingrese a Nearby primero"));
		if (u.getRole() == Role.ADMIN) {
			throw new BusinessRuleException(ALREADY_STAFF, "Esa persona administra la plataforma.");
		}
		if (u.getMunicipality() != null && !u.getMunicipality().getId().equals(m.getId())) {
			throw new ConflictException(ALREADY_STAFF, "Esa persona ya es parte de otra Municipalidad.");
		}
		u.setRole(Role.MUNICIPALITY);
		u.setMunicipality(m);
		return new StaffDto(u.getId(), u.getName(), u.getPhone(), u.getPhotoUrl(), u.getId().equals(actorId));
	}

	/** Saca a un funcionario del equipo: vuelve a ser vecino. Nadie se saca a sí mismo. */
	@Transactional
	public void removeStaff(UUID actorId, UUID userId) {
		Municipality m = ownMunicipality(actorId);
		if (actorId.equals(userId)) {
			throw new BusinessRuleException(NOT_STAFF, "No puedes quitarte a ti mismo del equipo.");
		}
		AppUser u = user(userId);
		if (u.getMunicipality() == null || !u.getMunicipality().getId().equals(m.getId())) {
			throw new NotFoundException(NOT_STAFF, "Esa persona no es parte de tu equipo.");
		}
		u.setMunicipality(null);
		u.setRole(Role.NEIGHBOR);
	}

	/** Zonas que puedo administrar (ADMIN todas, Municipalidad su distrito, delegado su zona). */
	@Transactional(readOnly = true)
	public List<ZoneDto> zones(UUID actorId) {
		AppUser actor = user(actorId);
		return zones.findAll().stream()
				.filter(z -> Jurisdiction.covers(actor, z))
				.sorted(Comparator.comparing(z -> z.getName()))
				.map(ZoneDto::from)
				.toList();
	}

	/** Crea una zona: la Municipalidad en su distrito; el ADMIN en el distrito que indique. */
	@Transactional
	public ZoneDto createZone(UUID actorId, ZoneCreateRequest req) {
		AppUser actor = user(actorId);
		String district;
		if (actor.getRole() == Role.ADMIN) {
			if (req.district() == null || req.district().isBlank()) {
				throw new BusinessRuleException("DISTRICT_REQUIRED", "Indica el distrito de la zona.");
			}
			district = req.district();
		}
		else {
			district = ownMunicipality(actorId).getDistrict();
		}
		int radius = req.radiusM() != null ? req.radiusM() : ZoneFactory.DEFAULT_RADIUS_M;
		return ZoneDto.from(zoneFactory.create(req.name(), district, req.lat(), req.lng(), radius));
	}

	private Municipality ownMunicipality(UUID actorId) {
		AppUser actor = user(actorId);
		Municipality m = actor.getMunicipality();
		if (actor.getRole() != Role.MUNICIPALITY || m == null || !m.isApproved()) {
			throw new ForbiddenException(NOT_STAFF, "Solo el equipo de una Municipalidad aprobada puede hacer esto.");
		}
		return m;
	}

	private AppUser user(UUID id) {
		return users.findById(id).orElseThrow(() -> NotFoundException.of("el usuario"));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
