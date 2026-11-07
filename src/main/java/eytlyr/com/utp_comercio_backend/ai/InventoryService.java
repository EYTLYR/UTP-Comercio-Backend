package eytlyr.com.utp_comercio_backend.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.InventoryItemDto;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.InventorySnapshot;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.InventoryStats;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.SnapshotItem;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.emergency.EmergencyService;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

/** Inventario de supervivencia de la zona. Las vulnerabilidades son anónimas para los vecinos. */
@Service
public class InventoryService {

	private final CapabilityRepository capabilities;

	private final AppUserRepository users;

	private final Clock clock;

	public InventoryService(CapabilityRepository capabilities, AppUserRepository users, Clock clock) {
		this.capabilities = capabilities;
		this.users = users;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<InventoryItemDto> inventory(UUID userId, CapabilityKind kind) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			return List.of();
		}
		boolean brigade = EmergencyService.isBrigade(me);
		OffsetDateTime now = OffsetDateTime.now(clock);
		return capabilities.findActiveInZone(me.getZone().getId()).stream()
				.filter(c -> kind == null || c.getKind() == kind)
				.map(c -> {
					boolean anonymous = !brigade && c.getKind() == CapabilityKind.VULNERABILITY;
					return new InventoryItemDto(c.getId(), c.getTag(), c.getTag().label(), c.getKind(),
							anonymous ? "Vecino de " + c.getZoneLabel() : c.getDisplayName(), c.getZoneLabel(),
							anonymous ? null : c.getEvidenceText(), c.getSourceType(), AiText.ago(c.getCreatedAt(), now),
							anonymous ? GeoUtils.approx(c.getLat()) : c.getLat(),
							anonymous ? GeoUtils.approx(c.getLng()) : c.getLng());
				})
				.toList();
	}

	/** Versión compacta para el teléfono. La versión (ETag) cambia si cambia cualquier capacidad. */
	@Transactional(readOnly = true)
	public InventorySnapshot snapshot(UUID userId) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			return new InventorySnapshot("empty", OffsetDateTime.now(clock), List.of());
		}
		boolean brigade = EmergencyService.isBrigade(me);
		List<Capability> all = capabilities.findActiveInZone(me.getZone().getId());
		List<SnapshotItem> items = all.stream()
				.map(c -> {
					boolean anonymous = !brigade && c.getKind() == CapabilityKind.VULNERABILITY;
					return new SnapshotItem(c.getId(), c.getTag(), c.getKind(),
							anonymous || c.getUser() == null ? null : c.getUser().getId(),
							anonymous ? "Vecino de " + c.getZoneLabel() : c.getDisplayName(),
							anonymous ? GeoUtils.approx(c.getLat()) : c.getLat(),
							anonymous ? GeoUtils.approx(c.getLng()) : c.getLng());
				})
				.toList();
		StringBuilder fingerprint = new StringBuilder(brigade ? "B" : "N");
		all.forEach(c -> fingerprint.append(c.getId()).append(c.getUpdatedAt()));
		return new InventorySnapshot(sha256(fingerprint.toString()).substring(0, 16), OffsetDateTime.now(clock), items);
	}

	@Transactional(readOnly = true)
	public InventoryStats stats(UUID userId) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			return new InventoryStats(0, 0, 0);
		}
		List<Capability> all = capabilities.findActiveInZone(me.getZone().getId());
		return new InventoryStats(all.stream().filter(c -> c.getKind() == CapabilityKind.RESOURCE).count(),
				all.stream().filter(c -> c.getKind() == CapabilityKind.SKILL).count(),
				all.stream().filter(c -> c.getKind() == CapabilityKind.VULNERABILITY).count());
	}

	@Transactional(readOnly = true)
	public UUID zoneOf(UUID userId) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			throw new NotFoundException("ZONE_NOT_SET", "Todavía no tienes zona. Guarda tu ubicación primero.");
		}
		return me.getZone().getId();
	}

	private AppUser user(UUID userId) {
		return users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
	}

	private static String sha256(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
