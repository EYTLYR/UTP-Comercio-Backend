package eytlyr.com.utp_comercio_backend.user;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.locationtech.jts.geom.Point;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.zone.Zone;

/** Vecino. El celular es el login. */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser extends BaseEntity {

	@Column(name = "phone", nullable = false, length = 9, unique = true)
	private String phone;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 40)
	@Builder.Default
	private Role role = Role.NEIGHBOR;

	@Column(name = "verified", nullable = false)
	@Builder.Default
	private boolean verified = false;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "zone_id")
	private Zone zone;

	@Column(name = "lat")
	private Double lat;

	@Column(name = "lng")
	private Double lng;

	/**
	 * Punto PostGIS (SRID 4326) de la última ubicación. Es una columna generada por la base a partir de
	 * lat/lng (V4__postgis.sql): no se escribe desde Java, y Hibernate la vuelve a leer al insertar o
	 * actualizar. Se usa en {@link AppUserRepository#findWithinRadius} con ST_DWithin.
	 */
	@Generated(event = { EventType.INSERT, EventType.UPDATE })
	@Column(name = "location", insertable = false, updatable = false, columnDefinition = "geometry(Point,4326)")
	private Point location;

	@Column(name = "address_text", length = 200)
	private String addressText;

	/** Foto de perfil: URL relativa (/files/...). Null muestra las iniciales. */
	@Column(name = "photo_url", length = 300)
	private String photoUrl;

	@Column(name = "ai_consent", nullable = false)
	@Builder.Default
	private boolean aiConsent = false;

	@Column(name = "mode_dismissed_at")
	private OffsetDateTime modeDismissedAt;

	@Column(name = "local_emergency", nullable = false)
	@Builder.Default
	private boolean localEmergency = false;

}
