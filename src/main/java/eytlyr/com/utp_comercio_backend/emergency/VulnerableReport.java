package eytlyr.com.utp_comercio_backend.emergency;

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

import eytlyr.com.utp_comercio_backend.ai.Capability;
import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.zone.Zone;

/** Reporte del tablero humanitario. Dirección exacta solo para BRIGADIST y ADMIN. */
@Entity
@Table(name = "vulnerable_report", uniqueConstraints = {@UniqueConstraint(name = "uq_vulnerable_report_reporter_client", columnNames = {"reporter_id", "client_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VulnerableReport extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reporter_id")
	private AppUser reporter;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 40)
	private VulnerableCategory category;

	@Column(name = "detail", nullable = false, length = 1000)
	private String detail;

	@Enumerated(EnumType.STRING)
	@Column(name = "priority", nullable = false, length = 40)
	private Priority priority;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 40)
	@Builder.Default
	private ReportStatus status = ReportStatus.PENDING;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_to_id")
	private AppUser assignedTo;

	@Column(name = "assigned_label", length = 120)
	private String assignedLabel;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	@Column(name = "address_text", length = 200)
	private String addressText;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 40)
	@Builder.Default
	private ReportSource source = ReportSource.USER;

	@Column(name = "client_id")
	private UUID clientId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_capability_id")
	private Capability sourceCapability;

}
