package eytlyr.com.utp_comercio_backend.auth;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;

/** Código SMS de acceso (solo se guarda el hash). */
@Entity
@Table(name = "otp_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpCode extends BaseEntity {

	@Column(name = "phone", nullable = false, length = 9)
	private String phone;

	@Column(name = "code_hash", nullable = false, length = 100)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@Column(name = "attempts", nullable = false)
	@Builder.Default
	private int attempts = 0;

	@Column(name = "consumed", nullable = false)
	@Builder.Default
	private boolean consumed = false;

}
