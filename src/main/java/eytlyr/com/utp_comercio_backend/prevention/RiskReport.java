package eytlyr.com.utp_comercio_backend.prevention;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.zone.Zone;

/** Reporte de riesgo con foto (El Niño). */
@Entity
@Table(name = "risk_report", uniqueConstraints = {@UniqueConstraint(name = "uq_risk_report_reporter_client", columnNames = {"reporter_id", "client_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskReport extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reporter_id", nullable = false)
	private AppUser reporter;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 40)
	private RiskCategory category;

	@Column(name = "text", nullable = false, length = 1000)
	private String text;

	@Column(name = "photo_url", length = 500)
	private String photoUrl;

	@Column(name = "photo_analysis", length = 500)
	private String photoAnalysis;

	@Column(name = "severity_hint")
	private Double severityHint;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	@Column(name = "address_text", length = 200)
	private String addressText;

	@Column(name = "client_id")
	private UUID clientId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "risk_zone_id")
	private RiskZone riskZone;

}
