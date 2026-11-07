package eytlyr.com.utp_comercio_backend.ai;

import java.time.OffsetDateTime;

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
import eytlyr.com.utp_comercio_backend.emergency.VulnerableReport;
import eytlyr.com.utp_comercio_backend.user.AppUser;

/** Misión: el vecino más cercano con la capacidad justa para un reporte. */
@Entity
@Table(name = "mission_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissionAssignment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_id", nullable = false)
	private RescuePlan plan;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private VulnerableReport report;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "capability_id", nullable = false)
	private Capability capability;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(name = "distance_m", nullable = false)
	private int distanceM;

	@Column(name = "reason", nullable = false, length = 500)
	private String reason;

	@Column(name = "notified_at")
	private OffsetDateTime notifiedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 40)
	@Builder.Default
	private MissionStatus status = MissionStatus.PROPOSED;

}
