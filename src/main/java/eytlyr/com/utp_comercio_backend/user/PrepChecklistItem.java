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

/** Ítem marcado de la lista "Prepara tu casa" (El Niño). */
@Entity
@Table(name = "prep_checklist_item", uniqueConstraints = {@UniqueConstraint(name = "uq_prep_checklist_item_user_item_key", columnNames = {"user_id", "item_key"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrepChecklistItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(name = "item_key", nullable = false, length = 40)
	private String itemKey;

	@Column(name = "done", nullable = false)
	@Builder.Default
	private boolean done = false;

}
