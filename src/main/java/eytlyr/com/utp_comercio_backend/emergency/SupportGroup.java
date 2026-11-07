package eytlyr.com.utp_comercio_backend.emergency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import eytlyr.com.utp_comercio_backend.zone.Zone;

/** Grupo de apoyo del directorio de emergencia. */
@Entity
@Table(name = "support_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportGroup extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "icon", nullable = false, length = 40)
	private String icon;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "active_now", nullable = false)
	@Builder.Default
	private boolean activeNow = false;

	@Column(name = "schedule_text", length = 120)
	private String scheduleText;

	@Column(name = "contact_phone", length = 15)
	private String contactPhone;

	@Column(name = "members_count", nullable = false)
	@Builder.Default
	private int membersCount = 0;

}
