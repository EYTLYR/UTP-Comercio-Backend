package eytlyr.com.utp_comercio_backend.prevention;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.ai.Capability;
import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.user.AppUser;

/** Recurso de mitigación del inventario propuesto para una zona de riesgo. */
@Entity
@Table(name = "mitigation_match")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MitigationMatch extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "risk_zone_id", nullable = false)
	private RiskZone riskZone;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "capability_id", nullable = false)
	private Capability capability;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(name = "distance_m", nullable = false)
	private int distanceM;

	@Column(name = "coordinated_at")
	private OffsetDateTime coordinatedAt;

}
