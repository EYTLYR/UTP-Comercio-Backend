package eytlyr.com.utp_comercio_backend.emergency;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;
import eytlyr.com.utp_comercio_backend.user.AppUser;

/**
 * Solicitud de recurso con "Ping de Disponibilidad" enviada simultáneamente a proveedores cercanos.
 */
@Entity
@Table(name = "resource_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_id", nullable = false)
	private AppUser requester;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", length = 40)
	private ResourceType resourceType;

	@Enumerated(EnumType.STRING)
	@Column(name = "mitigation_type", length = 40)
	private MitigationType mitigationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	@Builder.Default
	private ResourceRequestStatus status = ResourceRequestStatus.PENDING;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	@Column(name = "note", length = 500)
	private String note;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "accepted_by_id")
	private AppUser acceptedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "accepted_listing_id")
	private ResourceListing acceptedListing;

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<ResourceRequestCandidate> candidates = new ArrayList<>();

}

