package eytlyr.com.utp_comercio_backend.zone;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.notification.Dispatch;
import eytlyr.com.utp_comercio_backend.sos.SosAlertRepository;
import eytlyr.com.utp_comercio_backend.sos.SosStatus;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.user.dto.UserDto;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ModeAction;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneDto;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneModeRequest;

/** Modos de la zona: NORMAL, PREVENTION (El Niño) y EMERGENCY; y lo que cada vecino ve en su app. */
@Service
public class ZoneService {

	private static final Logger log = LoggerFactory.getLogger(ZoneService.class);

	private final ZoneRepository zones;

	private final AppUserRepository users;

	private final SosAlertRepository sosAlerts;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final NearbyProperties.Emergency emergencyProps;

	public ZoneService(ZoneRepository zones, AppUserRepository users, SosAlertRepository sosAlerts,
			ApplicationEventPublisher events, Clock clock, NearbyProperties props) {
		this.zones = zones;
		this.users = users;
		this.sosAlerts = sosAlerts;
		this.events = events;
		this.clock = clock;
		this.emergencyProps = props.emergency();
	}

	@Transactional(readOnly = true)
	public ZoneDto current(UUID userId) {
		Zone z = user(userId).getZone();
		if (z == null) {
			throw new NotFoundException("ZONE_NOT_SET", "Todavía no tienes zona. Guarda tu ubicación primero.");
		}
		return ZoneDto.from(z);
	}

	/** Municipalidad o delegado: cambia el modo de toda la zona y avisa a los vecinos. */
	@Transactional
	public ZoneDto setMode(UUID zoneId, ZoneModeRequest req) {
		Zone z = zones.findById(zoneId).orElseThrow(() -> NotFoundException.of("la zona"));
		z.setMode(req.mode());
		z.setModeSource(ModeSource.MUNICIPALITY);
		z.setModeReason(req.reason());
		z.setModeActivatedAt(OffsetDateTime.now(clock));
		if (req.mode() == ZoneMode.PREVENTION) {
			z.setWeatherLevel(req.weatherLevel() != null ? req.weatherLevel() : WeatherLevel.ORANGE);
			z.setWeatherValidUntil(req.validUntil());
		}
		else if (req.mode() == ZoneMode.NORMAL) {
			z.setWeatherLevel(null);
			z.setWeatherValidUntil(null);
		}
		String title = switch (req.mode()) {
			case EMERGENCY -> "Modo emergencia activo: " + (req.reason() != null ? req.reason() : z.getName());
			case PREVENTION -> "Alerta por lluvias · nivel " + label(z.getWeatherLevel());
			case NORMAL -> "Tu zona volvió a la normalidad";
		};
		events.publishEvent(Dispatch.builder()
				.zone(z.getId(), title, req.reason() != null ? req.reason() : z.getName(),
						Map.of("type", "zone_mode", "mode", req.mode().name()))
				.build());
		return ZoneDto.from(z);
	}

	/**
	 * Si en la zona hay al menos N SOS activos en la ventana configurada (5 en 10 minutos por defecto),
	 * la zona pasa sola a EMERGENCY.
	 * @return true si se activó en esta llamada
	 */
	@Transactional
	public boolean autoActivateIfNeeded(Zone z) {
		if (z.getMode() == ZoneMode.EMERGENCY) {
			return false;
		}
		OffsetDateTime since = OffsetDateTime.now(clock).minusMinutes(emergencyProps.autoActivateWindowMinutes());
		long active = sosAlerts.countByZoneIdAndStatusAndCreatedAtAfter(z.getId(), SosStatus.ACTIVE, since);
		if (active < emergencyProps.autoActivateSosCount()) {
			return false;
		}
		z.setMode(ZoneMode.EMERGENCY);
		z.setModeSource(ModeSource.AUTO_SOS);
		z.setModeReason(active + " alertas SOS en " + emergencyProps.autoActivateWindowMinutes() + " minutos");
		z.setModeActivatedAt(OffsetDateTime.now(clock));
		log.warn("Zona {} pasó a EMERGENCY automáticamente ({} SOS activos)", z.getName(), active);
		events.publishEvent(Dispatch.builder()
				.zone(z.getId(), "Modo emergencia activo", "Varios vecinos pidieron ayuda. Revisa el tablero.",
						Map.of("type", "zone_mode", "mode", "EMERGENCY"))
				.build());
		return true;
	}

	/** Lo que el vecino ve en SU app, sin cambiar el modo de la zona. */
	@Transactional
	public UserDto meMode(UUID userId, ModeAction action) {
		AppUser u = user(userId);
		switch (action) {
			case DISMISS -> {
				u.setModeDismissedAt(OffsetDateTime.now(clock));
				u.setLocalEmergency(false);
			}
			case ACTIVATE_LOCAL_EMERGENCY -> u.setLocalEmergency(true);
			case CLEAR_LOCAL -> u.setLocalEmergency(false);
		}
		return UserDto.from(u);
	}

	/** Zona para un SOS: la del vecino; si no tiene, la que cubre el punto; si ninguna, la más cercana. */
	@Transactional(readOnly = true)
	public Zone resolve(AppUser user, double lat, double lng) {
		if (user.getZone() != null) {
			return user.getZone();
		}
		return zones.findAll().stream()
				.min(Comparator.comparingDouble(z -> GeoUtils.haversineMeters(lat, lng, z.getCenterLat(), z.getCenterLng())
						/ Math.max(1, z.getRadiusM())))
				.orElseThrow(() -> new BusinessRuleException("ZONE_NOT_COVERED", "Nearby aún no llega a tu zona"));
	}

	private AppUser user(UUID userId) {
		return users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
	}

	private static String label(WeatherLevel level) {
		return switch (level) {
			case YELLOW -> "amarillo";
			case ORANGE -> "naranja";
			case RED -> "rojo";
		};
	}
}
