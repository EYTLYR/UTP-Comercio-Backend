package eytlyr.com.utp_comercio_backend.sos;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.Created;
import eytlyr.com.utp_comercio_backend.common.ForbiddenException;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.notification.Dispatch;
import eytlyr.com.utp_comercio_backend.sms.SosCodec;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.CheckInDto;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.NearbySosDto;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.ResponderDto;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.SosCreatedDto;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.SosDetailDto;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.SosRequest;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.user.Role;
import eytlyr.com.utp_comercio_backend.user.TrustedContact;
import eytlyr.com.utp_comercio_backend.user.TrustedContactRepository;
import eytlyr.com.utp_comercio_backend.zone.StreetCodeRepository;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneService;

/**
 * El botón SOS. Todo lo que toca servicios externos (push, SMS) sale en un Dispatch después del commit,
 * así el POST responde rápido y nunca falla por el proveedor.
 */
@Service
public class SosService {

	public static final String SOS_NOT_OWNER = "SOS_NOT_OWNER";

	public static final String SOS_CLOSED = "SOS_CLOSED";

	static final int ALERT_RADIUS_M = 500;

	static final int DECLINED_RADIUS_M = 1000;

	static final int WALKING_M_PER_MIN = 80;

	private final SosAlertRepository alerts;

	private final SosResponseRepository responses;

	private final CheckInRepository checkIns;

	private final AppUserRepository users;

	private final TrustedContactRepository contacts;

	private final StreetCodeRepository streets;

	private final ZoneService zones;

	private final SosCodec codec;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public SosService(SosAlertRepository alerts, SosResponseRepository responses, CheckInRepository checkIns,
			AppUserRepository users, TrustedContactRepository contacts, StreetCodeRepository streets, ZoneService zones,
			SosCodec codec, ApplicationEventPublisher events, Clock clock) {
		this.alerts = alerts;
		this.responses = responses;
		this.checkIns = checkIns;
		this.users = users;
		this.contacts = contacts;
		this.streets = streets;
		this.zones = zones;
		this.codec = codec;
		this.events = events;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- crear y cerrar

	@Transactional
	public Created<SosCreatedDto> create(UUID userId, SosRequest req) {
		if (req.clientId() != null) {
			var existing = alerts.findByUserIdAndClientId(userId, req.clientId());
			if (existing.isPresent()) {
				return new Created<>(created(existing.get(), 0, 0, 0, false), false);
			}
		}
		AppUser me = user(userId);
		Zone zone = zones.resolve(me, req.lat(), req.lng());
		String smsCode = codec.forSos(req.type(), streets.findByZoneId(zone.getId()), req.lat(), req.lng());
		SosAlert alert = alerts.save(SosAlert.builder()
				.user(me)
				.zone(zone)
				.type(req.type())
				.lat(req.lat())
				.lng(req.lng())
				.note(req.note())
				.smsCode(smsCode)
				.clientId(req.clientId())
				.build());

		// Destinatarios: vecinos a 500 m (por su última ubicación) y la brigada de la zona.
		List<AppUser> zoneUsers = users.findByZoneId(zone.getId());
		List<AppUser> neighbors = zoneUsers.stream()
				.filter(u -> !u.getId().equals(userId) && u.getRole() != Role.BRIGADIST)
				.filter(u -> within(u, req.lat(), req.lng(), ALERT_RADIUS_M))
				.toList();
		List<AppUser> brigade = zoneUsers.stream()
				.filter(u -> u.getRole() == Role.BRIGADIST && !u.getId().equals(userId))
				.toList();
		List<TrustedContact> myContacts = contacts.findByUserIdOrderByPosition(userId);

		Map<String, String> data = Map.of("type", "sos", "sosId", alert.getId().toString());
		String title = "SOS: " + me.getName() + " necesita ayuda";
		Dispatch.Builder d = Dispatch.builder();
		neighbors.forEach(u -> d.push(u.getId(), title, distanceText(u, req.lat(), req.lng()), data));
		brigade.forEach(u -> d.push(u.getId(), title, "Brigada: revisa el tablero", data));
		// 5 decimales (~1 m) para que el SMS entre en 160 caracteres.
		String sms = "NEARBY SOS: " + me.getName() + " necesita ayuda. Ubicación: https://maps.google.com/?q="
				+ String.format(java.util.Locale.ROOT, "%.5f,%.5f", req.lat(), req.lng()) + " · Código " + smsCode;
		myContacts.forEach(c -> d.sms(c.getPhone(), sms));
		events.publishEvent(d.build());

		alert.setNotifiedCount(neighbors.size() + brigade.size());
		boolean zoneActivated = zones.autoActivateIfNeeded(zone);
		return new Created<>(created(alert, neighbors.size(), myContacts.size(), brigade.size(), zoneActivated), true);
	}

	@Transactional
	public SosDetailDto changeType(UUID userId, UUID sosId, SosType type) {
		SosAlert a = owned(userId, sosId);
		a.setType(type);
		a.setSmsCode(codec.forSos(type, streets.findByZoneId(a.getZone().getId()), a.getLat(), a.getLng()));
		return detail(a);
	}

	/** "Estoy a salvo": cierra el SOS y avisa a los contactos. */
	@Transactional
	public SosDetailDto markSafe(UUID userId, UUID sosId) {
		SosAlert a = owned(userId, sosId);
		ensureActive(a);
		a.setStatus(SosStatus.SAFE);
		a.setClosedAt(OffsetDateTime.now(clock));
		Dispatch.Builder d = Dispatch.builder();
		String name = a.getUser().getName();
		contacts.findByUserIdOrderByPosition(userId).forEach(c -> d.sms(c.getPhone(), "NEARBY: " + name + " está a salvo."));
		responses.findByAlertIdOrderByCreatedAt(sosId).forEach(r -> d.push(r.getResponder().getId(),
				name + " está a salvo", "Gracias por responder", Map.of("type", "sos_safe", "sosId", sosId.toString())));
		events.publishEvent(d.build());
		return detail(a);
	}

	/** Cancelar (por ejemplo, un toque por error). No envía nada más. */
	@Transactional
	public SosDetailDto cancel(UUID userId, UUID sosId) {
		SosAlert a = owned(userId, sosId);
		ensureActive(a);
		a.setStatus(SosStatus.CANCELLED);
		a.setClosedAt(OffsetDateTime.now(clock));
		return detail(a);
	}

	// ---------------------------------------------------------------- consultar y responder

	@Transactional(readOnly = true)
	public SosDetailDto get(UUID sosId) {
		return detail(alert(sosId));
	}

	@Transactional(readOnly = true)
	public List<NearbySosDto> nearby(UUID userId, int radiusM) {
		AppUser me = user(userId);
		if (me.getZone() == null || me.getLat() == null || me.getLng() == null) {
			return List.of();
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		return alerts.findByZoneAndStatus(me.getZone().getId(), SosStatus.ACTIVE).stream()
				.filter(a -> !a.getUser().getId().equals(userId))
				.map(a -> new NearbySosDto(a.getId(), a.getUser().getName(), a.getType(),
						meters(me.getLat(), me.getLng(), a.getLat(), a.getLng()),
						Duration.between(a.getCreatedAt(), now).toMinutes(),
						responses.countByAlertIdAndStatus(a.getId(), ResponseStatus.GOING), GeoUtils.approx(a.getLat()),
						GeoUtils.approx(a.getLng())))
				.filter(a -> a.distanceM() <= radiusM)
				.sorted((x, y) -> Integer.compare(x.distanceM(), y.distanceM()))
				.toList();
	}

	/** GOING (voy, con tiempo estimado caminando), DECLINED (no puedo: se avisa más lejos) o ARRIVED (llegué). */
	@Transactional
	public SosDetailDto respond(UUID userId, UUID sosId, ResponseStatus status) {
		SosAlert a = alert(sosId);
		ensureActive(a);
		if (a.getUser().getId().equals(userId)) {
			throw new BusinessRuleException("SOS_SELF", "No puedes responder a tu propio SOS.");
		}
		AppUser me = user(userId);
		Integer distance = me.getLat() != null && me.getLng() != null
				? meters(me.getLat(), me.getLng(), a.getLat(), a.getLng())
				: null;
		SosResponse r = responses.findByAlertIdAndResponderId(sosId, userId)
				.orElseGet(() -> SosResponse.builder().alert(a).responder(me).build());
		r.setStatus(status);
		r.setEtaMinutes(status == ResponseStatus.GOING && distance != null
				? Math.max(1, (int) Math.ceil(distance / (double) WALKING_M_PER_MIN))
				: null);
		responses.save(r);

		Map<String, String> data = Map.of("type", "sos_response", "sosId", sosId.toString());
		Dispatch.Builder d = Dispatch.builder();
		switch (status) {
			case GOING -> d.push(a.getUser().getId(), me.getName() + " va en camino",
					r.getEtaMinutes() != null ? "Llega en unos " + r.getEtaMinutes() + " min" : "Va en camino", data);
			case DECLINED -> users.findByZoneId(a.getZone().getId()).stream()
					.filter(u -> !u.getId().equals(userId) && !u.getId().equals(a.getUser().getId()))
					.filter(u -> within(u, a.getLat(), a.getLng(), DECLINED_RADIUS_M)
							&& !within(u, a.getLat(), a.getLng(), ALERT_RADIUS_M))
					.forEach(u -> d.push(u.getId(), "SOS: " + a.getUser().getName() + " necesita ayuda",
							distanceText(u, a.getLat(), a.getLng()), data));
			case ARRIVED -> {
				d.push(a.getUser().getId(), me.getName() + " llegó", "Ya está contigo", data);
				users.findByZoneIdAndRole(a.getZone().getId(), Role.BRIGADIST).forEach(b -> d.push(b.getId(),
						me.getName() + " llegó al SOS de " + a.getUser().getName(), "Brigada: actualiza el tablero", data));
			}
		}
		events.publishEvent(d.build());
		return detail(a);
	}

	// ---------------------------------------------------------------- check-in

	/** "Estoy bien" / "Necesito ayuda" durante una emergencia. SAFE avisa a los contactos por SMS. */
	@Transactional
	public CheckInDto checkIn(UUID userId, CheckInStatus status) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			throw new BusinessRuleException("ZONE_REQUIRED", "Primero guarda tu ubicación.");
		}
		CheckIn c = checkIns.save(CheckIn.builder().user(me).zone(me.getZone()).status(status).build());
		int notified = 0;
		if (status == CheckInStatus.SAFE) {
			Dispatch.Builder d = Dispatch.builder();
			List<TrustedContact> list = contacts.findByUserIdOrderByPosition(userId);
			list.forEach(t -> d.sms(t.getPhone(), "NEARBY: " + me.getName() + " está bien."));
			events.publishEvent(d.build());
			notified = list.size();
		}
		return new CheckInDto(c.getId(), c.getStatus(), notified, c.getCreatedAt());
	}

	// ---------------------------------------------------------------- utilidades

	private SosAlert alert(UUID sosId) {
		return alerts.findWithUser(sosId).orElseThrow(() -> NotFoundException.of("el SOS"));
	}

	private SosAlert owned(UUID userId, UUID sosId) {
		SosAlert a = alert(sosId);
		if (!a.getUser().getId().equals(userId)) {
			throw new ForbiddenException(SOS_NOT_OWNER, "Solo quien envió el SOS puede cambiarlo.");
		}
		return a;
	}

	private static void ensureActive(SosAlert a) {
		if (a.getStatus() != SosStatus.ACTIVE) {
			throw new BusinessRuleException(SOS_CLOSED, "Este SOS ya fue cerrado.");
		}
	}

	private AppUser user(UUID userId) {
		return users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
	}

	private SosDetailDto detail(SosAlert a) {
		List<ResponderDto> list = responses.findByAlertIdOrderByCreatedAt(a.getId()).stream()
				.map(r -> {
					AppUser u = r.getResponder();
					Integer d = u.getLat() != null && u.getLng() != null
							? meters(u.getLat(), u.getLng(), a.getLat(), a.getLng())
							: null;
					return new ResponderDto(u.getId(), u.getName(), r.getStatus(), d, r.getEtaMinutes());
				})
				.toList();
		return new SosDetailDto(a.getId(), a.getUser().getId(), a.getUser().getName(), a.getType(), a.getStatus(),
				a.getNote(), a.getLat(), a.getLng(), a.getSmsCode(), a.getCreatedAt(), a.getClosedAt(), list);
	}

	private static SosCreatedDto created(SosAlert a, int neighbors, int contacts, int brigade, boolean zoneActivated) {
		return new SosCreatedDto(a.getId(), a.getStatus(), neighbors, contacts, brigade, a.getSmsCode(), zoneActivated,
				a.getCreatedAt());
	}

	private static boolean within(AppUser u, double lat, double lng, int radiusM) {
		return u.getLat() != null && u.getLng() != null && GeoUtils.haversineMeters(u.getLat(), u.getLng(), lat, lng) <= radiusM;
	}

	private static String distanceText(AppUser u, double lat, double lng) {
		return "A " + meters(u.getLat(), u.getLng(), lat, lng) + " m de ti";
	}

	private static int meters(double lat1, double lng1, double lat2, double lng2) {
		return (int) Math.round(GeoUtils.haversineMeters(lat1, lng1, lat2, lng2));
	}
}
