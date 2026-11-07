package eytlyr.com.utp_comercio_backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;

/** Ficha médica. Solo la ven BRIGADIST y ADMIN. */
@Entity
@Table(name = "medical_card")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalCard extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private AppUser user;

	@Column(name = "blood_type", length = 5)
	private String bloodType;

	@Column(name = "allergies", length = 300)
	private String allergies;

	@Column(name = "conditions", length = 300)
	private String conditions;

	@Column(name = "lives_with", length = 300)
	private String livesWith;

}
