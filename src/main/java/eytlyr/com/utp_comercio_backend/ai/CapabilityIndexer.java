package eytlyr.com.utp_comercio_backend.ai;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import eytlyr.com.utp_comercio_backend.ai.CapabilityExtractor.ExtractedCapability;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.emergency.ListingKind;
import eytlyr.com.utp_comercio_backend.emergency.ResourceListing;
import eytlyr.com.utp_comercio_backend.emergency.ResourceListingRepository;
import eytlyr.com.utp_comercio_backend.posts.Post;
import eytlyr.com.utp_comercio_backend.posts.PostRepository;
import eytlyr.com.utp_comercio_backend.posts.PostType;
import eytlyr.com.utp_comercio_backend.services.NeighborService;
import eytlyr.com.utp_comercio_backend.services.NeighborServiceRepository;
import eytlyr.com.utp_comercio_backend.services.Review;
import eytlyr.com.utp_comercio_backend.services.ReviewRepository;
import eytlyr.com.utp_comercio_backend.services.ServiceCategory;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.zone.StreetCode;
import eytlyr.com.utp_comercio_backend.zone.StreetCodeRepository;
import eytlyr.com.utp_comercio_backend.zone.Zone;

/**
 * Protocolo Lázaro, parte 1: la IA silenciosa del día a día.
 * Convierte publicaciones, oficios, reseñas y listados en capacidades del inventario, solo con consentimiento
 * y sin recrear nunca lo que el vecino borró.
 */
@Service
public class CapabilityIndexer {

	private static final Logger log = LoggerFactory.getLogger(CapabilityIndexer.class);

	static final double CATEGORY_CONFIDENCE = 0.9;

	private final CapabilityExtractor extractor;

	private final CapabilityRepository capabilities;

	private final PostRepository posts;

	private final NeighborServiceRepository services;

	private final ReviewRepository reviews;

	private final ResourceListingRepository listings;

	private final StreetCodeRepository streets;

	private final boolean indexOnEvents;

	public CapabilityIndexer(CapabilityExtractor extractor, CapabilityRepository capabilities, PostRepository posts,
			NeighborServiceRepository services, ReviewRepository reviews, ResourceListingRepository listings,
			StreetCodeRepository streets, NearbyProperties props) {
		this.extractor = extractor;
		this.capabilities = capabilities;
		this.posts = posts;
		this.services = services;
		this.reviews = reviews;
		this.listings = listings;
		this.streets = streets;
		this.indexOnEvents = props.ai().indexOnEvents();
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onContent(ContentCreatedEvent event) {
		if (!indexOnEvents) {
			return;
		}
		try {
			index(event.sourceType(), event.sourceId());
		}
		catch (RuntimeException ex) {
			log.warn("No se pudo indexar {} {}: {}", event.sourceType(), event.sourceId(), ex.getMessage());
		}
	}

	/** @return cuántas capacidades se crearon o actualizaron */
	@Transactional
	public int index(SourceType type, UUID sourceId) {
		return switch (type) {
			case POST -> posts.findById(sourceId).map(this::indexPost).orElse(0);
			case SERVICE -> services.findById(sourceId).map(this::indexService).orElse(0);
			case REVIEW -> reviews.findById(sourceId).map(this::indexReview).orElse(0);
			case RESOURCE_LISTING -> listings.findById(sourceId).map(this::indexListing).orElse(0);
		};
	}

	/** Reprocesa todo el contenido de la zona (POST /api/v1/admin/ai/reindex y datos semilla). */
	@Transactional
	public int reindexZone(UUID zoneId) {
		int n = 0;
		for (Post p : posts.findByZoneId(zoneId)) {
			n += indexPost(p);
		}
		for (NeighborService s : services.findActiveInZone(zoneId)) {
			n += indexService(s);
		}
		for (Review r : reviews.findInZone(zoneId)) {
			n += indexReview(r);
		}
		for (ResourceListing l : listings.findActiveInZone(zoneId)) {
			n += indexListing(l);
		}
		log.info("Inventario de la zona {} reindexado: {} capacidades", zoneId, n);
		return n;
	}

	// ---------------------------------------------------------------- por tipo de contenido

	/** En un "Necesito", el vecino no tiene el recurso: solo cuentan las vulnerabilidades. */
	private int indexPost(Post p) {
		boolean onlyVulnerabilities = p.getType() == PostType.NEED;
		int n = 0;
		for (ExtractedCapability c : extractor.extract(p.getText(), SourceType.POST)) {
			if (!onlyVulnerabilities || c.tag().kind() == CapabilityKind.VULNERABILITY) {
				n += upsert(p.getAuthor(), p.getZone(), SourceType.POST, p.getId(), c, p.getLat(), p.getLng(),
						p.getCreatedAt());
			}
		}
		return n;
	}

	/** Oficio: palabras clave en título y descripción; gasfitería o electricidad = brigadista técnico. */
	private int indexService(NeighborService s) {
		AppUser owner = s.getOwner();
		if (owner.getZone() == null) {
			return 0;
		}
		String text = s.getTitle() + (s.getDescription() != null ? ". " + s.getDescription() : "");
		int n = 0;
		for (ExtractedCapability c : extractor.extract(text, SourceType.SERVICE)) {
			if (c.tag().kind() != CapabilityKind.VULNERABILITY) {
				n += upsert(owner, owner.getZone(), SourceType.SERVICE, s.getId(), c, s.getLat(), s.getLng(),
						s.getCreatedAt());
			}
		}
		if (s.getCategory() == ServiceCategory.PLUMBING || s.getCategory() == ServiceCategory.ELECTRICITY) {
			n += upsert(owner, owner.getZone(), SourceType.SERVICE, s.getId(),
					new ExtractedCapability(CapabilityTag.TECHNICIAN, CATEGORY_CONFIDENCE,
							"Ofrece " + s.getTitle().toLowerCase() + " en el barrio"),
					s.getLat(), s.getLng(), s.getCreatedAt());
		}
		return n;
	}

	/**
	 * Reseña: lo que cuenta del servicio ("me inyectó") es capacidad del dueño;
	 * una vulnerabilidad ("mi abuela") es de quien escribe.
	 */
	private int indexReview(Review r) {
		NeighborService s = r.getService();
		int n = 0;
		for (ExtractedCapability c : extractor.extract(r.getText(), SourceType.REVIEW)) {
			boolean vulnerability = c.tag().kind() == CapabilityKind.VULNERABILITY;
			AppUser who = vulnerability ? r.getAuthor() : s.getOwner();
			if (who.getZone() == null) {
				continue;
			}
			double lat = vulnerability && who.getLat() != null ? who.getLat() : s.getLat();
			double lng = vulnerability && who.getLng() != null ? who.getLng() : s.getLng();
			n += upsert(who, who.getZone(), SourceType.REVIEW, r.getId(), c, lat, lng, r.getCreatedAt());
		}
		return n;
	}

	private int indexListing(ResourceListing l) {
		boolean onlyVulnerabilities = l.getKind() == ListingKind.NEED;
		int n = 0;
		for (ExtractedCapability c : extractor.extract(l.getText(), SourceType.RESOURCE_LISTING)) {
			if (!onlyVulnerabilities || c.tag().kind() == CapabilityKind.VULNERABILITY) {
				n += upsert(l.getUser(), l.getZone(), SourceType.RESOURCE_LISTING, l.getId(), c, l.getLat(), l.getLng(),
						l.getCreatedAt());
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- upsert

	/** Respeta el consentimiento y nunca recrea lo que el vecino borró (misma fuente y etiqueta). */
	private int upsert(AppUser user, Zone zone, SourceType type, UUID sourceId, ExtractedCapability c, double lat,
			double lng, OffsetDateTime sourceCreatedAt) {
		if (!user.isAiConsent()) {
			return 0;
		}
		var existing = capabilities.findBySourceTypeAndSourceIdAndTag(type, sourceId, c.tag());
		if (existing.isPresent() && existing.get().isRemovedByUser()) {
			return 0;
		}
		List<StreetCode> zoneStreets = streets.findByZoneId(zone.getId());
		Capability cap = existing.orElseGet(() -> {
			Capability fresh = Capability.builder()
					.zone(zone)
					.user(user)
					.tag(c.tag())
					.kind(c.tag().kind())
					.sourceType(type)
					.sourceId(sourceId)
					.build();
			// La fecha de la capacidad es la del contenido: así la IA puede decir "hace 21 días".
			fresh.setCreatedAt(sourceCreatedAt);
			return fresh;
		});
		cap.setDisplayName(user.getName());
		cap.setZoneLabel(AiText.zoneLabel(zoneStreets, lat, lng));
		cap.setEvidenceText(c.evidence());
		cap.setConfidence(c.confidence());
		cap.setLat(lat);
		cap.setLng(lng);
		capabilities.save(cap);
		return 1;
	}
}
