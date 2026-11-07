package eytlyr.com.utp_comercio_backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Contacto de confianza (hasta 3) que recibe la ubicación en un SOS. */
@Entity
@Table(name = "trusted_contact", uniqueConstraints = {@UniqueConstraint(name = "uq_trusted_contact_user_position", columnNames = {"user_id", "position"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustedContact extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "phone", nullable = false, length = 15)
	private String phone;

	@Column(name = "position", nullable = false)
	private int position;

}
