package eytlyr.com.utp_comercio_backend.ai;

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

/** Capacidad del inventario de supervivencia extraída por la IA (con consentimiento). */
@Entity
@Table(name = "capability", uniqueConstraints = {@UniqueConstraint(name = "uq_capability_source_type_source_tag", columnNames = {"source_type", "source_id", "tag"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Capability extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private AppUser user;

	@Column(name = "display_name", nullable = false, length = 120)
	private String displayName;

	@Column(name = "zone_label", length = 40)
	private String zoneLabel;

	@Enumerated(EnumType.STRING)
	@Column(name = "tag", nullable = false, length = 40)
	private CapabilityTag tag;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 40)
	private CapabilityKind kind;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 40)
	private SourceType sourceType;

	@Column(name = "source_id", nullable = false)
	private UUID sourceId;

	@Column(name = "evidence_text", nullable = false, length = 1000)
	private String evidenceText;

	@Column(name = "confidence", nullable = false)
	private double confidence;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	@Column(name = "removed_by_user", nullable = false)
	@Builder.Default
	private boolean removedByUser = false;

}
