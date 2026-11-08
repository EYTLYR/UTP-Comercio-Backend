package eytlyr.com.utp_comercio_backend.user;

import java.time.OffsetDateTime;
import java.util.UUID;

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

/** Pedido de un vecino para ser brigadista. Lo aprueba o rechaza la Municipalidad (ADMIN o DELEGATE). */
@Entity
@Table(name = "brigade_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrigadeRequest extends BaseEntity {

	public enum Status {
		PENDING, APPROVED, REJECTED
	}

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	/** Lo que sabe hacer: «Primeros auxilios, tengo camioneta». */
	@Column(name = "skills", length = 300)
	private String skills;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	@Builder.Default
	private Status status = Status.PENDING;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private OffsetDateTime decidedAt;
}
