package eytlyr.com.utp_comercio_backend.municipality;

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
import eytlyr.com.utp_comercio_backend.user.AppUser;

/**
 * Municipalidad registrada desde la app. La registra un funcionario y la aprueba el administrador de la
 * plataforma. Una vez aprobada administra las zonas cuyo distrito coincide con el suyo.
 */
@Entity
@Table(name = "municipality")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Municipality extends BaseEntity {

	public enum Status {
		PENDING, APPROVED, REJECTED
	}

	@Column(name = "name", nullable = false, length = 160)
	private String name;

	@Column(name = "district", nullable = false, length = 120)
	private String district;

	/** Distrito normalizado (minúsculas, sin tildes). */
	@Column(name = "district_key", nullable = false, length = 120)
	private String districtKey;

	@Column(name = "ruc", nullable = false, length = 11)
	private String ruc;

	@Column(name = "contact_name", nullable = false, length = 120)
	private String contactName;

	@Column(name = "contact_position", length = 120)
	private String contactPosition;

	@Column(name = "email", nullable = false, length = 160)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	@Builder.Default
	private Status status = Status.PENDING;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requested_by", nullable = false)
	private AppUser requestedBy;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private OffsetDateTime decidedAt;

	@Column(name = "reject_reason", length = 300)
	private String rejectReason;

	public boolean isApproved() {
		return status == Status.APPROVED;
	}
}
