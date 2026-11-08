package eytlyr.com.utp_comercio_backend.zone;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "zone_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneStats {

	@Id
	@Column(name = "zone_id")
	private UUID zoneId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "zone_id")
	private Zone zone;

	@Column(name = "favors_resolved", nullable = false)
	@Builder.Default
	private int favorsResolved = 0;

	@Column(name = "created_at", nullable = false)
	@Builder.Default
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "updated_at", nullable = false)
	@Builder.Default
	private OffsetDateTime updatedAt = OffsetDateTime.now();

}
