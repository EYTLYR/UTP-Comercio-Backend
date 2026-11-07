package eytlyr.com.utp_comercio_backend.sos;

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
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.zone.Zone;

/** Alerta SOS de un vecino. */
@Entity
@Table(name = "sos_alert", uniqueConstraints = {@UniqueConstraint(name = "uq_sos_alert_user_client", columnNames = {"user_id", "client_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SosAlert extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", length = 40)
	private SosType type;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	@Column(name = "note", length = 500)
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 40)
	@Builder.Default
	private SosStatus status = SosStatus.ACTIVE;

	@Column(name = "sms_code", length = 25)
	private String smsCode;

	@Column(name = "notified_count", nullable = false)
	@Builder.Default
	private int notifiedCount = 0;

	@Column(name = "client_id")
	private UUID clientId;

	@Column(name = "closed_at")
	private OffsetDateTime closedAt;

}
