package eytlyr.com.utp_comercio_backend.map;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.ai.CapabilityRepository;
import eytlyr.com.utp_comercio_backend.ai.CapabilityTag;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.emergency.EmergencyService;
import eytlyr.com.utp_comercio_backend.emergency.ListingKind;
import eytlyr.com.utp_comercio_backend.emergency.ReportStatus;
import eytlyr.com.utp_comercio_backend.emergency.ResourceListingRepository;
import eytlyr.com.utp_comercio_backend.emergency.SafePointRepository;
import eytlyr.com.utp_comercio_backend.emergency.SafePointType;
import eytlyr.com.utp_comercio_backend.emergency.SupportGroupRepository;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableReportRepository;
import eytlyr.com.utp_comercio_backend.posts.PostRepository;
import eytlyr.com.utp_comercio_backend.posts.PostStatus;
import eytlyr.com.utp_comercio_backend.posts.PostType;
import eytlyr.com.utp_comercio_backend.prevention.RiskReportRepository;
import eytlyr.com.utp_comercio_backend.services.NeighborServiceRepository;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneRepository;

/** Pines del mapa por capa. Los íconos son nombres de Material Symbols, los mismos que usa la app. */
@Service
public class MapService {

	public enum Layer {
		DAILY, EMERGENCY, PREVENTION
	}

	public record Pin(UUID id, String kind, double lat, double lng, String icon, String title, String subtitle,
			String refType, UUID refId) {
	}

	static final EnumSet<CapabilityTag> MITIGATION_TAGS = EnumSet.of(CapabilityTag.SANDBAGS, CapabilityTag.PUMP,
			CapabilityTag.ROOF_COVER, CapabilityTag.SHOVELS);

	private final AppUserRepository users;

	private final NeighborServiceRepository services;

	private final PostRepository posts;

	private final VulnerableReportRepository reports;

	private final SafePointRepository safePoints;

	private final ResourceListingRepository listings;

	private final SupportGroupRepository groups;

	private final RiskReportRepository risks;

	private final CapabilityRepository capabilities;

	private final Clock clock;

	private final ZoneRepository zones;

	public MapService(AppUserRepository users, NeighborServiceRepository services, PostRepository posts,
			VulnerableReportRepository reports, SafePointRepository safePoints, ResourceListingRepository listings,
			SupportGroupRepository groups, RiskReportRepository risks, CapabilityRepository capabilities, Clock clock,
			ZoneRepository zones) {
		this.zones = zones;
		this.users = users;
		this.services = services;
		this.posts = posts;
		this.reports = reports;
		this.safePoints = safePoints;
		this.listings = listings;
		this.groups = groups;
		this.risks = risks;
		this.capabilities = capabilities;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Pin> pins(UUID userId, Layer layer, int radiusM) {
		AppUser me = users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
		Zone zone = me.getZone();
		if (zone == null) {
			return List.of();
		}
		return pinsAround(me, zone, layer, radiusM);
	}

	/** Pines alrededor de un punto (mi casa o el centro de la zona que estoy mirando). */
	@Transactional(readOnly = true)
	public List<Pin> pinsInZone(UUID userId, UUID zoneId, Layer layer, int radiusM) {
		AppUser me = users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
		Zone zone = zones.findById(zoneId).orElseThrow(() -> NotFoundException.of("la zona"));
		return pinsAround(me, zone, layer, Math.max(radiusM, zone.getRadiusM()), zone.getCenterLat(),
				zone.getCenterLng());
	}

	private List<Pin> pinsAround(AppUser me, Zone zone, Layer layer, int radiusM) {
		double refLat = me.getLat() != null ? me.getLat() : zone.getCenterLat();
		double refLng = me.getLng() != null ? me.getLng() : zone.getCenterLng();
		return pinsAround(me, zone, layer, radiusM, refLat, refLng);
	}

	private List<Pin> pinsAround(AppUser me, Zone zone, Layer layer, int radiusM, double refLat, double refLng) {
		UUID zoneId = zone.getId();
		double[] box = GeoUtils.box(refLat, refLng, radiusM);
		List<Pin> pins = new ArrayList<>();
		switch (layer) {
			case DAILY -> {
				services.findActiveNear(box[0], box[1], box[2], box[3]).forEach(s -> pins.add(new Pin(s.getId(), "svc",
						s.getLat(), s.getLng(), serviceIcon(s.getCategory().name()),
						s.getOwner().getName() + " · " + s.getTitle(),
						s.getCategory().label() + " · " + s.getEndorsementCount() + " vistos buenos", "service",
						s.getId())));
				posts.findNear(box[0], box[1], box[2], box[3], EnumSet.of(PostStatus.OPEN)).forEach(p -> pins.add(
						new Pin(p.getId(), p.getType() == PostType.NEED ? "need" : "offer", p.getLat(), p.getLng(),
								p.getType() == PostType.NEED ? "front_hand" : "volunteer_activism",
								p.getAuthor().getName() + (p.getType() == PostType.NEED ? " necesita ayuda" : " ofrece ayuda"),
								p.getCategory().label() + (p.getText() != null && !p.getText().isBlank()
										? ": " + p.getText() : ""),
								"post", p.getId())));
			}
			case EMERGENCY -> {
				boolean exact = EmergencyService.isBrigade(me);
				reports.findInZone(zoneId, EnumSet.of(ReportStatus.PENDING, ReportStatus.ON_THE_WAY))
						.forEach(r -> pins.add(new Pin(r.getId(), "vuln", exact ? r.getLat() : GeoUtils.approx(r.getLat()),
								exact ? r.getLng() : GeoUtils.approx(r.getLng()), vulnerableIcon(r.getCategory().name()),
								r.getCategory().label(), r.getPriority().name().equals("HIGH") ? "Prioridad alta"
										: "Prioridad media", "vulnerable_report", r.getId())));
				addSafePoints(pins, zoneId);
				listings.findActiveInZone(zoneId).stream()
						.filter(l -> l.getResourceType() != null && l.getKind() == ListingKind.HAVE)
						.forEach(l -> pins.add(new Pin(l.getId(), "res", l.getLat(), l.getLng(),
								resourceIcon(l.getResourceType().name()), l.getResourceType().label(), l.getText(),
								"resource", l.getId())));
				groups.findByZoneIdOrderByName(zoneId).forEach(g -> pins.add(new Pin(g.getId(), "group",
						zone.getCenterLat(), zone.getCenterLng(), g.getIcon(), g.getName(),
						g.getMembersCount() + " miembros", "support_group", g.getId())));
			}
			case PREVENTION -> {
				risks.findRecentInZone(zoneId, OffsetDateTime.now(clock).minusHours(24)).forEach(r -> pins.add(new Pin(
						r.getId(), "risk", r.getLat(), r.getLng(), riskIcon(r.getCategory().name()),
						r.getCategory().label(), r.getAddressText(), "risk_report", r.getId())));
				capabilities.findByZoneIdAndTagInAndRemovedByUserFalse(zoneId, MITIGATION_TAGS).forEach(c -> pins.add(
						new Pin(c.getId(), "mitig", c.getLat(), c.getLng(), mitigationIcon(c.getTag().name()),
								c.getTag().label(), c.getDisplayName(), "capability", c.getId())));
				listings.findActiveInZone(zoneId).stream()
						.filter(l -> l.getMitigationType() != null && l.getKind() == ListingKind.HAVE)
						.forEach(l -> pins.add(new Pin(l.getId(), "mitig", l.getLat(), l.getLng(),
								mitigationIcon(l.getMitigationType().name()), l.getMitigationType().label(), l.getText(),
								"resource", l.getId())));
				addSafePoints(pins, zoneId);
			}
		}
		return pins.stream()
				.filter(p -> GeoUtils.haversineMeters(refLat, refLng, p.lat(), p.lng()) <= radiusM)
				.toList();
	}

	private void addSafePoints(List<Pin> pins, UUID zoneId) {
		safePoints.findByZoneIdOrderByName(zoneId).forEach(p -> pins.add(new Pin(p.getId(), "safe", p.getLat(), p.getLng(),
				p.getType() == SafePointType.SHELTER ? "night_shelter" : "flag", p.getName(),
				p.getCapacity() != null ? p.getOccupied() + " de " + p.getCapacity() + " cupos" : "Punto de reunión",
				"safe_point", p.getId())));
	}

	static String serviceIcon(String category) {
		return switch (category) {
			case "PLUMBING" -> "plumbing";
			case "ELECTRICITY" -> "electrical_services";
			case "CARPENTRY" -> "carpenter";
			case "LOCKSMITH" -> "key";
			case "TUTORING" -> "school";
			case "PET_CARE" -> "pets";
			case "SEWING" -> "styler";
			default -> "handyman";
		};
	}

	static String vulnerableIcon(String category) {
		return switch (category) {
			case "ELDERLY_ALONE" -> "elderly";
			case "DISABILITY" -> "accessible";
			case "CHILDREN_ALONE" -> "child_care";
			case "INJURED" -> "personal_injury";
			case "PREGNANT" -> "pregnant_woman";
			case "TRAPPED" -> "warning";
			default -> "pulmonology";
		};
	}

	static String resourceIcon(String type) {
		return switch (type) {
			case "WATER" -> "water_drop";
			case "FOOD" -> "restaurant";
			case "MEDICINE" -> "medical_services";
			case "ENERGY" -> "bolt";
			case "SHELTER" -> "night_shelter";
			default -> "local_shipping";
		};
	}

	static String riskIcon(String category) {
		return switch (category) {
			case "ACEQUIA_OVERFLOW" -> "water";
			case "STREET_FLOODING" -> "flood";
			case "SEEPAGE" -> "water_damage";
			case "RAVINE_ACTIVITY" -> "landslide";
			case "DRAIN_OVERFLOW" -> "waves";
			default -> "crisis_alert";
		};
	}

	static String mitigationIcon(String tag) {
		return switch (tag) {
			case "SANDBAGS" -> "inventory_2";
			case "PUMP" -> "water_pump";
			case "ROOF_COVER" -> "roofing";
			case "SHOVELS" -> "construction";
			case "PLANKS" -> "carpenter";
			default -> "flashlight_on";
		};
	}
}
