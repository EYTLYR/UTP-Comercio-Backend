package eytlyr.com.utp_comercio_backend.emergency;

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

/** Punto de reunión o refugio. */
@Entity
@Table(name = "safe_point")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafePoint extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 40)
	private SafePointType type;

	@Column(name = "capacity")
	private Integer capacity;

	@Column(name = "occupied")
	private Integer occupied;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	@jakarta.persistence.Version
	@Column(name = "version")
	private Long version;

}
