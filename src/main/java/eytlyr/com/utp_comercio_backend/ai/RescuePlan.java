package eytlyr.com.utp_comercio_backend.ai;

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

import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.zone.Zone;

/** Plan de rescate generado por la IA en una emergencia. */
@Entity
@Table(name = "rescue_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescuePlan extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@Column(name = "generated_at", nullable = false)
	private OffsetDateTime generatedAt;

	@Column(name = "duration_ms", nullable = false)
	private long durationMs;

	@Column(name = "missions_count", nullable = false)
	@Builder.Default
	private int missionsCount = 0;

	@Column(name = "preventive_reports_count", nullable = false)
	@Builder.Default
	private int preventiveReportsCount = 0;

}
