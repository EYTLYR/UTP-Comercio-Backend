package eytlyr.com.utp_comercio_backend.sos;

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

/** Vecino que responde a un SOS (voy, no puedo, llegué). */
@Entity
@Table(name = "sos_response", uniqueConstraints = {@UniqueConstraint(name = "uq_sos_response_alert_responder", columnNames = {"alert_id", "responder_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SosResponse extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alert_id", nullable = false)
	private SosAlert alert;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "responder_id", nullable = false)
	private AppUser responder;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 40)
	@Builder.Default
	private ResponseStatus status = ResponseStatus.GOING;

	@Column(name = "eta_minutes")
	private Integer etaMinutes;

}
