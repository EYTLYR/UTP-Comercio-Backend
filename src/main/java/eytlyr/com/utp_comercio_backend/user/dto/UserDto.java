package eytlyr.com.utp_comercio_backend.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.Role;
import eytlyr.com.utp_comercio_backend.zone.ModeSource;
import eytlyr.com.utp_comercio_backend.zone.WeatherLevel;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;

/**
 * Perfil propio del vecino. effectiveMode es el modo que la app debe mostrar.
 * Nunca incluye la dirección ni la ficha médica.
 */
public record UserDto(UUID id, String name, String phone, Role role, boolean verified, ZoneSummary zone,
		boolean aiConsent, ZoneMode effectiveMode, String photoUrl, MunicipalitySummary municipality) {

	/** Municipalidad de la que el usuario es parte (null si no es parte de ninguna). */
	public record MunicipalitySummary(UUID id, String name, String district) {
	}

	/** modeReason y modeActivatedAt: motivo y hora en que se activó el modo actual (null en NORMAL). */
	public record ZoneSummary(UUID id, String name, ZoneMode mode, ModeSource modeSource, WeatherLevel weatherLevel,
			String modeReason, OffsetDateTime modeActivatedAt, double centerLat, double centerLng, int radiusM) {

		static ZoneSummary from(Zone z) {
			boolean normal = z.getMode() == ZoneMode.NORMAL;
			return new ZoneSummary(z.getId(), z.getName(), z.getMode(), z.getModeSource(), z.getWeatherLevel(),
					normal ? null : z.getModeReason(), normal ? null : z.getModeActivatedAt(), z.getCenterLat(),
					z.getCenterLng(), z.getRadiusM());
		}
	}

	/** Debe llamarse dentro de una transacción (la zona es LAZY). */
	public static UserDto from(AppUser u) {
		Zone z = u.getZone();
		return new UserDto(u.getId(), u.getName(), u.getPhone(), u.getRole(), u.isVerified(),
				z != null ? ZoneSummary.from(z) : null, u.isAiConsent(), effectiveMode(u), u.getPhotoUrl(),
				u.getMunicipality() != null
						? new MunicipalitySummary(u.getMunicipality().getId(), u.getMunicipality().getName(),
								u.getMunicipality().getDistrict())
						: null);
	}

	/**
	 * 1. Emergencia activada solo para mí → EMERGENCY.
	 * 2. La zona no está en NORMAL y no salí de ese modo después de que se activó → modo de la zona.
	 * 3. Si no → NORMAL.
	 */
	public static ZoneMode effectiveMode(AppUser u) {
		if (u.isLocalEmergency()) {
			return ZoneMode.EMERGENCY;
		}
		Zone z = u.getZone();
		if (z != null && z.getMode() != ZoneMode.NORMAL) {
			OffsetDateTime dismissed = u.getModeDismissedAt();
			OffsetDateTime activated = z.getModeActivatedAt();
			if (dismissed == null || (activated != null && dismissed.isBefore(activated))) {
				return z.getMode();
			}
		}
		return ZoneMode.NORMAL;
	}
}
