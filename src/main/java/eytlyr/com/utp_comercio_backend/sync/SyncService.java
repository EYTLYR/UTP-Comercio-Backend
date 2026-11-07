package eytlyr.com.utp_comercio_backend.sync;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.ai.InventoryService;
import eytlyr.com.utp_comercio_backend.common.BaseEntity;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.emergency.EmergencyService;
import eytlyr.com.utp_comercio_backend.emergency.SafePointRepository;
import eytlyr.com.utp_comercio_backend.posts.Post;
import eytlyr.com.utp_comercio_backend.posts.PostRepository;
import eytlyr.com.utp_comercio_backend.posts.PostStatus;
import eytlyr.com.utp_comercio_backend.prevention.PreventionService;
import eytlyr.com.utp_comercio_backend.services.NeighborService;
import eytlyr.com.utp_comercio_backend.services.NeighborServiceRepository;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.Deleted;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.SyncPost;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.SyncResponse;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.SyncSafePoint;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.ServiceItem;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.SyncStreet;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.zone.StreetCodeRepository;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.dto.ZoneDtos.ZoneDto;

/**
 * Paquete para guardar la zona en el teléfono. Con "since" solo viene lo cambiado desde esa fecha;
 * los datos pequeños (zona, calles, grupos, inventario, zonas de riesgo) siempre vienen completos.
 */
@Service("offlineSyncService")
public class SyncService {

	static final int MAX_ITEMS = 500;

	private final AppUserRepository users;

	private final NeighborServiceRepository services;

	private final PostRepository posts;

	private final SafePointRepository safePoints;

	private final StreetCodeRepository streets;

	private final EmergencyService emergency;

	private final InventoryService inventory;

	private final PreventionService prevention;

	private final Clock clock;

	public SyncService(AppUserRepository users, NeighborServiceRepository services, PostRepository posts,
			SafePointRepository safePoints, StreetCodeRepository streets, EmergencyService emergency,
			InventoryService inventory, PreventionService prevention, Clock clock) {
		this.users = users;
		this.services = services;
		this.posts = posts;
		this.safePoints = safePoints;
		this.streets = streets;
		this.emergency = emergency;
		this.inventory = inventory;
		this.prevention = prevention;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public SyncResponse sync(UUID userId, OffsetDateTime since) {
		AppUser me = users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
		Zone zone = me.getZone();
		if (zone == null) {
			throw new NotFoundException("ZONE_NOT_SET", "Todavía no tienes zona. Guarda tu ubicación primero.");
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		UUID zoneId = zone.getId();

		// Servicios: activos cambiados = actualizados; inactivos = borrados.
		List<NeighborService> allServices = services.findAll().stream()
				.filter(s -> s.getOwner().getZone() != null && s.getOwner().getZone().getId().equals(zoneId))
				.filter(changedSince(since))
				.toList();
		List<ServiceItem> svc = allServices.stream().filter(NeighborService::isActive).limit(MAX_ITEMS)
				.map(s -> new ServiceItem(s.getId(), s.getTitle(), s.getCategory(), s.getCategory().label(),
						s.getOwner().getName(), s.getEndorsementCount(), s.isAvailableInEmergency(), s.getPriceText(),
						s.getLat(), s.getLng(), s.getUpdatedAt()))
				.toList();

		// Publicaciones: abiertas o emparejadas = actualizadas; canceladas o terminadas = borradas.
		List<Post> allPosts = posts.findByZoneId(zoneId).stream().filter(changedSince(since)).toList();
		List<SyncPost> pst = allPosts.stream()
				.filter(p -> p.getStatus() == PostStatus.OPEN || p.getStatus() == PostStatus.MATCHED)
				.limit(MAX_ITEMS)
				.map(p -> new SyncPost(p.getId(), p.getType(), p.getCategory(), p.getText(), p.getAuthor().getName(),
						p.getStatus(), p.getLat(), p.getLng(), p.getCreatedAt(), p.getUpdatedAt()))
				.toList();

		Deleted deleted = new Deleted(
				allPosts.stream().filter(p -> p.getStatus() == PostStatus.CANCELLED || p.getStatus() == PostStatus.DONE)
						.map(Post::getId).toList(),
				allServices.stream().filter(s -> !s.isActive()).map(NeighborService::getId).toList());

		return new SyncResponse(now, since == null, ZoneDto.from(zone), svc, pst,
				safePoints.findByZoneIdOrderByName(zoneId).stream()
						.map(p -> new SyncSafePoint(p.getId(), p.getName(), p.getType(), p.getCapacity(), p.getOccupied(),
								p.getLat(), p.getLng()))
						.toList(),
				emergency.groups(userId),
				streets.findByZoneId(zoneId).stream().map(s -> new SyncStreet(s.getCode(), s.getName(), s.getLat(), s.getLng()))
						.toList(),
				emergency.reports(userId, null, 0, 50).content(), // respeta la privacidad según el rol
				inventory.snapshot(userId), prevention.zones(userId), deleted);
	}

	private static <T extends BaseEntity> java.util.function.Predicate<T> changedSince(OffsetDateTime since) {
		Function<T, OffsetDateTime> updated = BaseEntity::getUpdatedAt;
		return e -> since == null || updated.apply(e).isAfter(since);
	}
}
