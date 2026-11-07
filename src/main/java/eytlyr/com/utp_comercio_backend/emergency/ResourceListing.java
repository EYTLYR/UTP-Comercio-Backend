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
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.zone.Zone;

/** Tengo / necesito un recurso. Exactamente uno: resourceType (emergencia) o mitigationType (prevención). */
@Entity
@Table(name = "resource_listing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceListing extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", length = 40)
	private ResourceType resourceType;

	@Enumerated(EnumType.STRING)
	@Column(name = "mitigation_type", length = 40)
	private MitigationType mitigationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 40)
	private ListingKind kind;

	@Column(name = "text", nullable = false, length = 500)
	private String text;

	@Column(name = "quantity", nullable = false)
	@Builder.Default
	private int quantity = 1;

	@Column(name = "location_text", length = 200)
	private String locationText;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	@Column(name = "active", nullable = false)
	@Builder.Default
	private boolean active = true;

}
