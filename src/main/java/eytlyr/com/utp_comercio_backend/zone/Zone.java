package eytlyr.com.utp_comercio_backend.zone;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;

/** Zona (barrio) con su modo actual: NORMAL, PREVENTION o EMERGENCY. */
@Entity
@Table(name = "zone")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone extends BaseEntity {

	@Column(name = "name", nullable = false, length = 120, unique = true)
	private String name;

	@Column(name = "district", nullable = false, length = 120)
	private String district;

	@Column(name = "center_lat", nullable = false)
	private double centerLat;

	@Column(name = "center_lng", nullable = false)
	private double centerLng;

	@Column(name = "radius_m", nullable = false)
	private int radiusM;

	@Enumerated(EnumType.STRING)
	@Column(name = "mode", nullable = false, length = 40)
	@Builder.Default
	private ZoneMode mode = ZoneMode.NORMAL;

	@Enumerated(EnumType.STRING)
	@Column(name = "mode_source", length = 40)
	private ModeSource modeSource;

	@Column(name = "mode_reason", length = 300)
	private String modeReason;

	@Column(name = "mode_activated_at")
	private OffsetDateTime modeActivatedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "weather_level", length = 40)
	private WeatherLevel weatherLevel;

	@Column(name = "weather_valid_until")
	private OffsetDateTime weatherValidUntil;

}
