package eytlyr.com.utp_comercio_backend.services;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.ai.ContentCreatedEvent;
import eytlyr.com.utp_comercio_backend.ai.SourceType;
import eytlyr.com.utp_comercio_backend.chat.ChatService;
import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.ForbiddenException;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.common.PageResponse;
import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ConversationRef;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ReviewDto;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ReviewRequest;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ServiceDetailDto;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ServiceRequest;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ServiceSummaryDto;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

/** Directorio de oficios (Módulo 1). El Visto Bueno está en EndorsementService. */
@Service
public class ServiceCatalogService {

	public static final String LOCATION_REQUIRED = "LOCATION_REQUIRED";

	public static final String SERVICE_NOT_OWNER = "SERVICE_NOT_OWNER";

	public record SearchQuery(ServiceCategory category, String q, Double lat, Double lng, int radiusM,
			boolean emergencyOnly, String sort, int page, int size) {
	}

	private final NeighborServiceRepository services;

	private final EndorsementRepository endorsements;

	private final ReviewRepository reviews;

	private final AppUserRepository users;

	private final ChatService chat;

	private final ApplicationEventPublisher events;

	public ServiceCatalogService(NeighborServiceRepository services, EndorsementRepository endorsements,
			ReviewRepository reviews, AppUserRepository users, ChatService chat, ApplicationEventPublisher events) {
		this.services = services;
		this.endorsements = endorsements;
		this.reviews = reviews;
		this.users = users;
		this.chat = chat;
		this.events = events;
	}

	/**
	 * Búsqueda en memoria: una zona tiene decenas de oficios, no miles. Así la búsqueda sin tildes
	 * y por raíz ("gasfitero" → "Gasfitería") es simple y no depende de extensiones de PostgreSQL.
	 */
	@Transactional(readOnly = true)
	public PageResponse<ServiceSummaryDto> search(UUID userId, SearchQuery query) {
		AppUser me = user(userId);
		Double refLat = query.lat() != null ? query.lat() : me.getLat();
		Double refLng = query.lng() != null ? query.lng() : me.getLng();
		boolean hasRef = refLat != null && refLng != null;
		Set<UUID> mine = endorsements.serviceIdsEndorsedBy(userId);

		List<NeighborService> candidates = me.getZone() != null
				? services.findActiveInZone(me.getZone().getId())
				: services.findAllActive();

		List<ServiceSummaryDto> all = candidates.stream()
				.filter(s -> query.category() == null || s.getCategory() == query.category())
				.filter(s -> !query.emergencyOnly() || s.isAvailableInEmergency())
				.filter(s -> TextUtils.matches(query.q(), s.getTitle(), s.getDescription(), s.getOwner().getName(),
						s.getCategory().label()))
				.map(s -> summary(s, hasRef ? distance(refLat, refLng, s) : null, mine.contains(s.getId())))
				.filter(s -> s.distanceM() == null || s.distanceM() <= query.radiusM())
				.sorted("distance".equals(query.sort()) && hasRef
						? Comparator.comparing(ServiceSummaryDto::distanceM)
						: Comparator.comparing(ServiceSummaryDto::endorsementCount).reversed())
				.toList();

		int size = Math.max(1, Math.min(query.size(), 50));
		int page = Math.max(0, query.page());
		int from = Math.min(page * size, all.size());
		int to = Math.min(from + size, all.size());
		return new PageResponse<>(all.subList(from, to), page, size, all.size(), (all.size() + size - 1) / size);
	}

	@Transactional(readOnly = true)
	public ServiceDetailDto detail(UUID userId, UUID serviceId) {
		AppUser me = user(userId);
		NeighborService s = service(serviceId);
		AppUser owner = s.getOwner();
		Integer distance = me.getLat() != null && me.getLng() != null ? distance(me.getLat(), me.getLng(), s) : null;
		List<String> initials = endorsements.findTop4ByServiceIdOrderByCreatedAtDesc(serviceId).stream()
				.map(e -> TextUtils.initials(e.getUser().getName()))
				.toList();
		List<ReviewDto> latest = reviews.findByServiceIdOrderByCreatedAtDesc(serviceId, PageRequest.of(0, 3)).stream()
				.map(ServiceCatalogService::review)
				.toList();
		return new ServiceDetailDto(s.getId(), owner.getName(), s.getTitle(), s.getCategory(), s.getCategory().label(),
				distance, s.getEndorsementCount(), endorsements.existsByServiceIdAndUserId(serviceId, userId),
				s.isAvailableInEmergency(), s.getPriceText(), s.getDescription(), s.getScheduleText(),
				owner.isVerified(), owner.getZone() != null ? owner.getZone().getName() : null, initials, latest,
				TextUtils.maskPhone(owner.getPhone()), owner.getId().equals(userId));
	}

	@Transactional
	public ServiceDetailDto create(UUID userId, ServiceRequest req) {
		AppUser me = user(userId);
		if (me.getLat() == null || me.getLng() == null) {
			throw new BusinessRuleException(LOCATION_REQUIRED, "Primero guarda tu ubicación para ofrecer tu oficio.");
		}
		NeighborService s = NeighborService.builder().owner(me).lat(me.getLat()).lng(me.getLng()).build();
		apply(s, req);
		services.save(s);
		events.publishEvent(new ContentCreatedEvent(SourceType.SERVICE, s.getId()));
		return detail(userId, s.getId());
	}

	@Transactional
	public ServiceDetailDto update(UUID userId, UUID serviceId, ServiceRequest req) {
		NeighborService s = owned(userId, serviceId);
		apply(s, req);
		events.publishEvent(new ContentCreatedEvent(SourceType.SERVICE, s.getId()));
		return detail(userId, serviceId);
	}

	/** Borrado lógico: el servicio deja de aparecer, pero sus vistos buenos y reseñas se conservan. */
	@Transactional
	public void delete(UUID userId, UUID serviceId) {
		owned(userId, serviceId).setActive(false);
	}

	@Transactional(readOnly = true)
	public List<ReviewDto> reviews(UUID serviceId) {
		service(serviceId);
		return reviews.findByServiceIdOrderByCreatedAtDesc(serviceId, PageRequest.of(0, 50)).stream()
				.map(ServiceCatalogService::review)
				.toList();
	}

	@Transactional
	public ReviewDto addReview(UUID userId, UUID serviceId, ReviewRequest req) {
		NeighborService s = service(serviceId);
		if (s.getOwner().getId().equals(userId)) {
			throw new BusinessRuleException("REVIEW_SELF", "No puedes reseñar tu propio servicio.");
		}
		Review r = reviews.save(Review.builder().service(s).author(user(userId)).text(req.text().trim()).build());
		events.publishEvent(new ContentCreatedEvent(SourceType.REVIEW, r.getId()));
		return review(r);
	}

	/** Botón "Mensaje": conversación con el dueño del servicio. */
	@Transactional
	public ConversationRef contact(UUID userId, UUID serviceId) {
		NeighborService s = service(serviceId);
		return new ConversationRef(chat.getOrCreate(user(userId), s.getOwner(), null, s, "Sobre: " + s.getTitle()).getId());
	}

	// ---------------------------------------------------------------- utilidades

	NeighborService service(UUID serviceId) {
		NeighborService s = services.findById(serviceId).orElseThrow(() -> NotFoundException.of("el servicio"));
		if (!s.isActive()) {
			throw NotFoundException.of("el servicio");
		}
		return s;
	}

	private NeighborService owned(UUID userId, UUID serviceId) {
		NeighborService s = service(serviceId);
		if (!s.getOwner().getId().equals(userId)) {
			throw new ForbiddenException(SERVICE_NOT_OWNER, "Solo el dueño puede cambiar este servicio.");
		}
		return s;
	}

	private AppUser user(UUID userId) {
		return users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
	}

	private static void apply(NeighborService s, ServiceRequest req) {
		s.setTitle(req.title().trim());
		s.setCategory(req.category());
		s.setDescription(req.description());
		s.setPriceText(req.priceText());
		s.setScheduleText(req.scheduleText());
		s.setAvailableInEmergency(req.availableInEmergency());
	}

	private static int distance(double lat, double lng, NeighborService s) {
		return (int) Math.round(GeoUtils.haversineMeters(lat, lng, s.getLat(), s.getLng()));
	}

	private static ServiceSummaryDto summary(NeighborService s, Integer distance, boolean endorsedByMe) {
		return new ServiceSummaryDto(s.getId(), s.getOwner().getName(), s.getTitle(), s.getCategory(),
				s.getCategory().label(), distance, s.getEndorsementCount(), endorsedByMe, s.isAvailableInEmergency(),
				s.getPriceText());
	}

	private static ReviewDto review(Review r) {
		return new ReviewDto(r.getId(), r.getAuthor().getName(), TextUtils.initials(r.getAuthor().getName()), r.getText(),
				r.getCreatedAt());
	}
}
