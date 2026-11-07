package eytlyr.com.utp_comercio_backend.prevention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.zone.Zone;

/** Zona de riesgo calculada por la IA agrupando reportes cercanos (AMBER o RED). */
@Entity
@Table(name = "risk_zone")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskZone extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "level", nullable = false, length = 40)
	private RiskLevel level;

	@Column(name = "centroid_lat", nullable = false)
	private double centroidLat;

	@Column(name = "centroid_lng", nullable = false)
	private double centroidLng;

	@Column(name = "report_count", nullable = false)
	@Builder.Default
	private int reportCount = 0;

	@Column(name = "score", nullable = false)
	private double score;

	@Column(name = "pattern_text", nullable = false, length = 1000)
	private String patternText;

	@Column(name = "homes_estimate", nullable = false)
	@Builder.Default
	private int homesEstimate = 0;

	@Column(name = "active", nullable = false)
	@Builder.Default
	private boolean active = true;

}
