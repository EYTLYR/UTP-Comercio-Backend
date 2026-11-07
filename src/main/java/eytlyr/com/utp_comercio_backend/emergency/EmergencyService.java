package eytlyr.com.utp_comercio_backend.emergency;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.ai.ContentCreatedEvent;
import eytlyr.com.utp_comercio_backend.ai.SourceType;
import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.Created;
import eytlyr.com.utp_comercio_backend.common.ForbiddenException;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.common.PageResponse;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.DashboardDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceRequest;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceSummaryDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.SafePointDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.SupportGroupDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.VulnerableReportDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.VulnerableReportRequest;
import eytlyr.com.utp_comercio_backend.notification.Dispatch;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;
import eytlyr.com.utp_comercio_backend.sos.CheckInRepository;
import eytlyr.com.utp_comercio_backend.sos.CheckInStatus;
import eytlyr.com.utp_comercio_backend.sos.SosAlertRepository;
import eytlyr.com.utp_comercio_backend.sos.SosStatus;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.user.Role;

/** Modo emergencia: tablero humanitario, recursos críticos, grupos de apoyo y puntos seguros. */
@Service
public class EmergencyService {

	public static final String REPORT_NOT_ALLOWED = "REPORT_NOT_ALLOWED";

	public static final String REPORT_CLOSED = "REPORT_CLOSED";

	private final VulnerableReportRepository reports;

	private final ResourceListingRepository listings;

	private final SupportGroupRepository groups;

	private final SupportGroupMemberRepository members;

	private final SafePointRepository safePoints;

	private final CheckInRepository checkIns;

	private final SosAlertRepository sosAlerts;

	private final AppUserRepository users;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public EmergencyService(VulnerableReportRepository reports, ResourceListingRepository listings,
			SupportGroupRepository groups, SupportGroupMemberRepository members, SafePointRepository safePoints,
			CheckInRepository checkIns, SosAlertRepository sosAlerts, AppUserRepository users,
			ApplicationEventPublisher events, Clock clock) {
		this.reports = reports;
		this.listings = listings;
		this.groups = groups;
		this.members = members;
		this.safePoints = safePoints;
		this.checkIns = checkIns;
		this.sosAlerts = sosAlerts;
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- tablero humanitario

	@Transactional(readOnly = true)
	public DashboardDto dashboard(UUID zoneId) {
		return new DashboardDto(reports.countByZoneId(zoneId), reports.countByZoneIdAndStatus(zoneId, ReportStatus.PENDING),
				reports.countByZoneIdAndStatus(zoneId, ReportStatus.ON_THE_WAY),
				reports.countByZoneIdAndStatus(zoneId, ReportStatus.ATTENDED),
				checkIns.countUsers(zoneId, CheckInStatus.SAFE, OffsetDateTime.now(clock).minusHours(24)),
				sosAlerts.countByZoneIdAndStatus(zoneId, SosStatus.ACTIVE));
	}

	@Transactional(readOnly = true)
	public PageResponse<VulnerableReportDto> reports(UUID userId, ReportStatus status, int page, int size) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			return new PageResponse<>(List.of(), page, size, 0, 0);
		}
		Set<ReportStatus> statuses = status != null ? EnumSet.of(status) : EnumSet.allOf(ReportStatus.class);
		List<VulnerableReportDto> all = reports.findInZone(me.getZone().getId(), statuses).stream()
				.sorted(Comparator.comparing((VulnerableReport r) -> r.getPriority() == Priority.HIGH ? 0 : 1))
				.map(r -> toDto(r, me))
				.toList();
		int s = Math.max(1, Math.min(size, 50));
		int pg = Math.max(0, page);
		int from = Math.min(pg * s, all.size());
		return new PageResponse<>(all.subList(from, Math.min(from + s, all.size())), pg, s, all.size(),
				(all.size() + s - 1) / s);
	}

	/** Reportar a un vecino vulnerable (idempotente por clientId). Avisa a la brigada de la zona. */
	@Transactional
	public Created<VulnerableReportDto> createReport(UUID userId, VulnerableReportRequest req) {
		AppUser me = user(userId);
		if (req.clientId() != null) {
			var existing = reports.findByReporterIdAndClientId(userId, req.clientId());
			if (existing.isPresent()) {
				return new Created<>(toDto(existing.get(), me), false);
			}
		}
		if (me.getZone() == null) {
			throw new BusinessRuleException("ZONE_REQUIRED", "Primero guarda tu ubicación.");
		}
		double lat = req.lat() != null ? req.lat() : me.getLat() != null ? me.getLat() : me.getZone().getCenterLat();
		double lng = req.lng() != null ? req.lng() : me.getLng() != null ? me.getLng() : me.getZone().getCenterLng();
		VulnerableReport r = reports.save(VulnerableReport.builder()
				.zone(me.getZone())
				.reporter(me)
				.category(req.category())
				.detail(req.detail() != null && !req.detail().isBlank() ? req.detail().trim() : req.category().label())
				.priority(req.priority() != null ? req.priority() : defaultPriority(req.category()))
				.lat(lat)
				.lng(lng)
				.addressText(req.addressText())
				.source(ReportSource.USER)
				.clientId(req.clientId())
				.build());
		Dispatch.Builder d = Dispatch.builder();
		users.findByZoneIdAndRole(me.getZone().getId(), Role.BRIGADIST).forEach(b -> d.push(b.getId(),
				"Nuevo reporte: " + r.getCategory().label(), r.getDetail(),
				Map.of("type", "vulnerable_report", "reportId", r.getId().toString())));
		events.publishEvent(d.build());
		return new Created<>(toDto(r, me), true);
	}

	/** "Voy yo": queda en camino y asignado a mí. */
	@Transactional
	public VulnerableReportDto assign(UUID userId, UUID reportId) {
		VulnerableReport r = report(reportId);
		if (r.getStatus() == ReportStatus.ATTENDED) {
			throw new BusinessRuleException(REPORT_CLOSED, "Este reporte ya fue atendido.");
		}
		AppUser me = user(userId);
		r.setStatus(ReportStatus.ON_THE_WAY);
		r.setAssignedTo(me);
		r.setAssignedLabel(me.getName());
		return toDto(r, me);
	}

	/** Cambiar el estado: quien está asignado, la brigada o el admin. */
	@Transactional
	public VulnerableReportDto updateStatus(UUID userId, UUID reportId, ReportStatus status) {
		VulnerableReport r = report(reportId);
		AppUser me = user(userId);
		boolean assigned = r.getAssignedTo() != null && r.getAssignedTo().getId().equals(userId);
		if (!assigned && !isBrigade(me)) {
			throw new ForbiddenException(REPORT_NOT_ALLOWED, "Solo quien atiende el reporte o la brigada pueden cambiarlo.");
		}
		r.setStatus(status);
		if (status == ReportStatus.PENDING) {
			r.setAssignedTo(null);
			r.setAssignedLabel(null);
		}
		return toDto(r, me);
	}

	// ---------------------------------------------------------------- recursos críticos

	/** Tengo / necesito por tipo de recurso (suma de cantidades). Siempre devuelve los 6 tipos. */
	@Transactional(readOnly = true)
	public List<ResourceSummaryDto> resourceSummary(UUID userId) {
		AppUser me = user(userId);
		Map<ResourceType, long[]> totals = new LinkedHashMap<>();
		for (ResourceType t : ResourceType.values()) {
			totals.put(t, new long[2]);
		}
		if (me.getZone() != null) {
			for (ResourceListing l : listings.findActiveInZone(me.getZone().getId())) {
				if (l.getResourceType() != null) {
					totals.get(l.getResourceType())[l.getKind() == ListingKind.HAVE ? 0 : 1] += Math.max(l.getQuantity(), 1);
				}
			}
		}
		List<ResourceSummaryDto> out = new ArrayList<>();
		totals.forEach((t, v) -> out.add(new ResourceSummaryDto(t.name(), t.label(), v[0], v[1])));
		return out;
	}

	@Transactional(readOnly = true)
	public List<ResourceDto> resources(UUID userId, ResourceType type, ListingKind kind) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			return List.of();
		}
		return listings.findActiveInZone(me.getZone().getId()).stream()
				.filter(l -> l.getResourceType() != null)
				.filter(l -> type == null || l.getResourceType() == type)
				.filter(l -> kind == null || l.getKind() == kind)
				.map(l -> toDto(l, me))
				.toList();
	}

	@Transactional
	public ResourceDto createResource(UUID userId, ResourceRequest req) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			throw new BusinessRuleException("ZONE_REQUIRED", "Primero guarda tu ubicación.");
		}
		ResourceListing l = listings.save(ResourceListing.builder()
				.user(me)
				.zone(me.getZone())
				.resourceType(req.resourceType())
				.kind(req.kind())
				.text(req.text().trim())
				.quantity(req.quantity() != null ? req.quantity() : 1)
				.locationText(req.locationText())
				.lat(me.getLat() != null ? me.getLat() : me.getZone().getCenterLat())
				.lng(me.getLng() != null ? me.getLng() : me.getZone().getCenterLng())
				.build());
		events.publishEvent(new ContentCreatedEvent(SourceType.RESOURCE_LISTING, l.getId()));
		return toDto(l, me);
	}

	// ---------------------------------------------------------------- grupos y puntos seguros

	@Transactional(readOnly = true)
	public List<SupportGroupDto> groups(UUID userId) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			return List.of();
		}
		Set<UUID> mine = members.groupIdsOf(userId);
		return groups.findByZoneIdOrderByName(me.getZone().getId()).stream()
				.map(g -> toDto(g, mine.contains(g.getId())))
				.toList();
	}

	@Transactional
	public SupportGroupDto join(UUID userId, UUID groupId) {
		SupportGroup g = groups.findById(groupId).orElseThrow(() -> NotFoundException.of("el grupo"));
		if (!members.existsByGroupIdAndUserId(groupId, userId)) {
			members.save(SupportGroupMember.builder().group(g).user(user(userId)).build());
			g.setMembersCount(g.getMembersCount() + 1);
		}
		return toDto(g, true);
	}

	@Transactional
	public SupportGroupDto leave(UUID userId, UUID groupId) {
		SupportGroup g = groups.findById(groupId).orElseThrow(() -> NotFoundException.of("el grupo"));
		members.findByGroupIdAndUserId(groupId, userId).ifPresent(m -> {
			members.delete(m);
			g.setMembersCount(Math.max(0, g.getMembersCount() - 1));
		});
		return toDto(g, false);
	}

	@Transactional(readOnly = true)
	public List<SafePointDto> safePoints(UUID userId) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			return List.of();
		}
		return safePoints.findByZoneIdOrderByName(me.getZone().getId()).stream()
				.map(p -> new SafePointDto(p.getId(), p.getName(), p.getType(), p.getCapacity(), p.getOccupied(),
						p.getLat(), p.getLng(), distance(me, p.getLat(), p.getLng())))
				.toList();
	}

	// ---------------------------------------------------------------- privacidad y utilidades

	public static boolean isBrigade(AppUser u) {
		return u.getRole() == Role.BRIGADIST || u.getRole() == Role.ADMIN;
	}

	/** "Mz. C lote 12" → "Mz. C"; "Jr. Los Pinos 340" → "Jr. Los Pinos". */
	public static String coarseAddress(String address) {
		if (address == null) {
			return null;
		}
		String a = address.replaceAll("(?i)\\s+lote\\s+\\d+.*$", "");
		a = a.replaceAll("\\s+\\d+[A-Za-z]?\\b.*$", "");
		return a.trim();
	}

	VulnerableReportDto toDto(VulnerableReport r, AppUser viewer) {
		boolean exact = isBrigade(viewer);
		return new VulnerableReportDto(r.getId(), r.getCategory(), r.getCategory().label(), r.getDetail(),
				r.getPriority(), r.getStatus(), r.getAssignedLabel(),
				r.getAssignedTo() != null && r.getAssignedTo().getId().equals(viewer.getId()), r.getSource(),
				r.getCreatedAt(), exact ? r.getAddressText() : coarseAddress(r.getAddressText()),
				exact ? r.getLat() : GeoUtils.approx(r.getLat()), exact ? r.getLng() : GeoUtils.approx(r.getLng()), exact);
	}

	private static Priority defaultPriority(VulnerableCategory c) {
		return switch (c) {
			case INJURED, TRAPPED, OXYGEN_DEPENDENT, CHILDREN_ALONE, ELDERLY_ALONE -> Priority.HIGH;
			case DISABILITY, PREGNANT -> Priority.MEDIUM;
		};
	}

	private ResourceDto toDto(ResourceListing l, AppUser viewer) {
		String label = l.getResourceType() != null ? l.getResourceType().label()
				: l.getMitigationType() != null ? l.getMitigationType().label() : null;
		return new ResourceDto(l.getId(), l.getResourceType(), l.getMitigationType(), label, l.getKind(), l.getText(),
				l.getQuantity(), l.getLocationText(), l.getUser().getName(), distance(viewer, l.getLat(), l.getLng()),
				l.getUser().getId().equals(viewer.getId()), l.getCreatedAt());
	}

	static ResourceDto mitigationDto(ResourceListing l, AppUser viewer) {
		MitigationType t = l.getMitigationType();
		return new ResourceDto(l.getId(), null, t, t != null ? t.label() : null, l.getKind(), l.getText(), l.getQuantity(),
				l.getLocationText(), l.getUser().getName(), distance(viewer, l.getLat(), l.getLng()),
				l.getUser().getId().equals(viewer.getId()), l.getCreatedAt());
	}

	private static SupportGroupDto toDto(SupportGroup g, boolean joined) {
		return new SupportGroupDto(g.getId(), g.getName(), g.getIcon(), g.getDescription(), g.isActiveNow(),
				g.getScheduleText(), g.getContactPhone(), g.getMembersCount(), joined);
	}

	private static Integer distance(AppUser viewer, double lat, double lng) {
		return viewer.getLat() != null && viewer.getLng() != null
				? (int) Math.round(GeoUtils.haversineMeters(viewer.getLat(), viewer.getLng(), lat, lng))
				: null;
	}

	private VulnerableReport report(UUID id) {
		return reports.findById(id).orElseThrow(() -> NotFoundException.of("el reporte"));
	}

	private AppUser user(UUID userId) {
		return users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
	}
}
