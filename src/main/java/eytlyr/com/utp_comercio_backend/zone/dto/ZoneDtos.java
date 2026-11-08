package eytlyr.com.utp_comercio_backend.zone.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.zone.ModeSource;
import eytlyr.com.utp_comercio_backend.zone.WeatherLevel;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;

public final class ZoneDtos {

	private ZoneDtos() {
	}

	public record ZoneDto(UUID id, String name, String district, ZoneMode mode, ModeSource modeSource,
			String modeReason, OffsetDateTime modeActivatedAt, WeatherLevel weatherLevel,
			OffsetDateTime weatherValidUntil, double centerLat, double centerLng, int radiusM) {

		public static ZoneDto from(Zone z) {
			return new ZoneDto(z.getId(), z.getName(), z.getDistrict(), z.getMode(), z.getModeSource(),
					z.getModeReason(), z.getModeActivatedAt(), z.getWeatherLevel(), z.getWeatherValidUntil(),
					z.getCenterLat(), z.getCenterLng(), z.getRadiusM());
		}
	}

	/** Zona nueva desde el panel de la Municipalidad (el ADMIN indica el distrito). */
	public record ZoneCreateRequest(
			@jakarta.validation.constraints.NotBlank(message = "Escribe el nombre de la zona") @Size(max = 110) String name,
			@NotNull(message = "Elige el centro de la zona") Double lat,
			@NotNull(message = "Elige el centro de la zona") Double lng,
			Integer radiusM,
			@Size(max = 120) String district) {
	}

	public record ZoneModeRequest(@NotNull(message = "Indica el modo") ZoneMode mode,
			@Size(max = 300, message = "El motivo no debe pasar de 300 caracteres") String reason,
			WeatherLevel weatherLevel, OffsetDateTime validUntil) {
	}

	public enum ModeAction {
		/** Volver al modo normal solo en mi app. */
		DISMISS,
		/** Activar el modo emergencia solo para mí. */
		ACTIVATE_LOCAL_EMERGENCY,
		/** Quitar mi emergencia local. */
		CLEAR_LOCAL
	}

	public record MeModeRequest(@NotNull(message = "Indica la acción") ModeAction action) {
	}
}
