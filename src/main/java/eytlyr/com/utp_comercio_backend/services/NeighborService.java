package eytlyr.com.utp_comercio_backend.services;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.user.AppUser;

/** Oficio del directorio con su contador de Visto Bueno Vecinal. */
@Entity
@Table(name = "neighbor_service")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NeighborService extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@Column(name = "title", nullable = false, length = 120)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 40)
	private ServiceCategory category;

	@Column(name = "description", length = 1000)
	private String description;

	@Column(name = "price_text", length = 80)
	private String priceText;

	@Column(name = "schedule_text", length = 80)
	private String scheduleText;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	@Column(name = "available_in_emergency", nullable = false)
	@Builder.Default
	private boolean availableInEmergency = false;

	@Column(name = "active", nullable = false)
	@Builder.Default
	private boolean active = true;

	@Column(name = "endorsement_count", nullable = false)
	@Builder.Default
	private int endorsementCount = 0;

	@Version
	@Column(name = "version")
	private Long version;

}
