package eytlyr.com.utp_comercio_backend.posts;

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

/** Publicación del muro de la cadena de favores (sin dinero). */
@Entity
@Table(name = "post", uniqueConstraints = {@UniqueConstraint(name = "uq_post_author_client", columnNames = {"author_id", "client_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private AppUser author;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "zone_id", nullable = false)
	private Zone zone;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 40)
	private PostType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 40)
	private PostCategory category;

	@Column(name = "text", nullable = false, length = 1000)
	private String text;

	@Column(name = "photo_url", length = 500)
	private String photoUrl;

	@Column(name = "lat", nullable = false)
	private double lat;

	@Column(name = "lng", nullable = false)
	private double lng;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 40)
	@Builder.Default
	private PostStatus status = PostStatus.OPEN;

	@Column(name = "client_id")
	private UUID clientId;

}
