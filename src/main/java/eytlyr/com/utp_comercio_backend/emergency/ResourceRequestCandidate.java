package eytlyr.com.utp_comercio_backend.emergency;

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
import eytlyr.com.utp_comercio_backend.user.AppUser;

/**
 * Proveedor candidato al que se le envió el ping de disponibilidad de un recurso.
 */
@Entity
@Table(name = "resource_request_candidate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceRequestCandidate extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private ResourceRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "provider_id", nullable = false)
	private AppUser provider;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "listing_id", nullable = false)
	private ResourceListing listing;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	@Builder.Default
	private CandidateStatus status = CandidateStatus.PENDING;

	@Column(name = "notified_at", nullable = false)
	@Builder.Default
	private OffsetDateTime notifiedAt = OffsetDateTime.now();

	@Column(name = "responded_at")
	private OffsetDateTime respondedAt;

}

