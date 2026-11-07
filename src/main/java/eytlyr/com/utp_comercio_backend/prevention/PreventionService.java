package eytlyr.com.utp_comercio_backend.prevention;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import eytlyr.com.utp_comercio_backend.ai.ContentCreatedEvent;
import eytlyr.com.utp_comercio_backend.ai.SourceType;
import eytlyr.com.utp_comercio_backend.ai.TextClassifier;
import eytlyr.com.utp_comercio_backend.chat.ChatService;
import eytlyr.com.utp_comercio_backend.chat.Conversation;
import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.Created;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.emergency.EmergencyService;
import eytlyr.com.utp_comercio_backend.emergency.ListingKind;
import eytlyr.com.utp_comercio_backend.emergency.ResourceListing;
import eytlyr.com.utp_comercio_backend.emergency.ResourceListingRepository;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceSummaryDto;
import eytlyr.com.utp_comercio_backend.notification.Dispatch;
import eytlyr.com.utp_comercio_backend.prevention.PhotoRiskAnalyzer.PhotoAnalysis;
import eytlyr.com.utp_comercio_backend.prevention.RiskClusteringService.Recompute;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.AnalyzeResult;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.CoordinateResult;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.HeatSpot;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.MitigationItem;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.MitigationRequest;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.PhotoAnalysisDto;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskReportCreated;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskReportDto;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskReportRequest;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskZoneDto;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.ZoneReportItem;
import eytlyr.com.utp_comercio_backend.sms.SosCodec;
import eytlyr.com.utp_comercio_backend.storage.StorageService;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.zone.StreetCode;
import eytlyr.com.utp_comercio_backend.zone.StreetCodeRepository;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;

/** Modo prevención (El Niño): vecinos como sensores, radar, coordinación de mitigación y mapa de calor. */
@Service
public class PreventionService {

	public static final String PHOTO_INVALID = "PHOTO_INVALID";

	static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

	static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png");

	private final RiskReportRepository reports;

	private final RiskZoneRepository riskZones;

	private final MitigationMatchRepository matches;

	private final RiskClusteringService clustering;

	private final PhotoRiskAnalyzer analyzer;

	private final StorageService storage;

	private final TextClassifier classifier;

	private final SosCodec codec;

	private final StreetCodeRepository streets;

	private final AppUserRepository users;

	private final ResourceListingRepository listings;

	private final ChatService chat;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public PreventionService(RiskReportRepository reports, RiskZoneRepository riskZones,
			MitigationMatchRepository matches, RiskClusteringService clustering, PhotoRiskAnalyzer analyzer,
			StorageService storage, TextClassifier classifier, SosCodec codec, StreetCodeRepository streets,
			AppUserRepository users, ResourceListingRepository listings, ChatService chat,
			ApplicationEventPublisher events, Clock clock) {
		this.reports = reports;
		this.riskZones = riskZones;
		this.matches = matches;
		this.clustering = clustering;
		this.analyzer = analyzer;
		this.storage = storage;
		this.classifier = classifier;
		this.codec = codec;
		this.streets = streets;
		this.users = users;
		this.listings = listings;
		this.chat = chat;
		this.events = events;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- reportes de riesgo

	/**
	 * Reporte con foto opcional (idempotente por clientId). Después de guardarlo se recalcula el radar en la
	 * misma transacción, para responder si la zona subió a RED con este reporte.
	 */
	@Transactional
	public Created<RiskReportCreated> createReport(UUID userId, RiskReportRequest req, MultipartFile photo) {
		AppUser me = user(userId);
		if (req.clientId() != null) {
			var existing = reports.findByReporterIdAndClientId(userId, req.clientId());
			if (existing.isPresent()) {
				RiskReport r = existing.get();
				return new Created<>(new RiskReportCreated(toDto(r), null,
						r.getRiskZone() != null ? zoneDto(r.getRiskZone()) : null, false), false);
			}
		}
		if (me.getZone() == null) {
			throw new BusinessRuleException("ZONE_REQUIRED", "Primero guarda tu ubicación.");
		}
		UUID zoneId = me.getZone().getId();
		List<StreetCode> zoneStreets = streets.findByZoneId(zoneId);

		// Categoría: la elegida, o la que propone la foto, o la del texto (micrófono inteligente).
		RiskCategory category = req.category();
		if (category == null && req.text() != null && !req.text().isBlank()) {
			String proposed = classifier.classify(req.text(), ZoneMode.PREVENTION).category();
			category = proposed != null && !"null".equals(proposed) ? RiskCategory.valueOf(proposed) : null;
		}
		PhotoAnalysis analysis = null;
		String photoUrl = null;
		if (photo != null && !photo.isEmpty()) {
			byte[] bytes = validPhoto(photo);
			photoUrl = storage.store(bytes, photo.getContentType());
			analysis = analyzer.analyze(bytes, photo.getContentType(), category);
			category = analysis.category();
		}
		if (category == null) {
			category = RiskCategory.STREET_FLOODING;
		}

		// Ubicación: la enviada; si no, la calle de la dirección; si no, la del vecino.
		double[] point = locate(req, zoneStreets, me);
		RiskReport r = reports.save(RiskReport.builder()
				.zone(me.getZone())
				.reporter(me)
				.category(category)
				.text(req.text() != null && !req.text().isBlank() ? req.text().trim() : category.label())
				.photoUrl(photoUrl)
				.photoAnalysis(analysis != null ? analysis.summary() : null)
				.severityHint(analysis != null ? analysis.severity() : null)
				.lat(point[0])
				.lng(point[1])
				.addressText(req.addressText())
				.clientId(req.clientId())
				.build());

		Recompute radar = clustering.recompute(zoneId);
		RiskZone rz = r.getRiskZone();
		boolean escalated = rz != null && radar.escalated().contains(rz.getId());
		PhotoAnalysisDto photoDto = analysis == null ? null
				: new PhotoAnalysisDto(analysis.category(), analysis.waterLevelCm(), analysis.severity(), analysis.summary());
		return new Created<>(new RiskReportCreated(toDto(r), photoDto, rz != null ? zoneDto(rz) : null, escalated), true);
	}

	@Transactional(readOnly = true)
	public List<RiskReportDto> recentReports(UUID userId, int hours) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			return List.of();
		}
		return reports.findRecentInZone(me.getZone().getId(), OffsetDateTime.now(clock).minusHours(Math.max(1, hours)))
				.stream()
				.map(this::toDto)
				.toList();
	}

	// ---------------------------------------------------------------- radar

	/** Botón "Analizar con IA". */
	@Transactional
	public AnalyzeResult analyze(UUID userId) {
		Recompute radar = clustering.recompute(zoneOf(userId));
		return new AnalyzeResult(radar.durationMs(), radar.zones().stream().map(this::zoneDto).toList(),
				List.copyOf(radar.escalated()));
	}

	@Transactional(readOnly = true)
	public List<RiskZoneDto> zones(UUID userId) {
		return riskZones.findByZoneIdAndActiveTrueOrderByScoreDesc(zoneOf(userId)).stream().map(this::zoneDto).toList();
	}

	/** "Sí, coordinar ayuda": chat con el primer vecino de la mitigación y aviso a las viviendas cercanas. */
	@Transactional
	public CoordinateResult coordinate(UUID userId, UUID riskZoneId) {
		RiskZone rz = riskZones.findById(riskZoneId).orElseThrow(() -> NotFoundException.of("la zona de riesgo"));
		List<MitigationMatch> list = matches.findByRiskZoneIdOrderByDistanceM(riskZoneId);
		if (list.isEmpty()) {
			throw new BusinessRuleException("MITIGATION_EMPTY", "No hay vecinos con recursos de mitigación cerca.");
		}
		AppUser me = user(userId);
		AppUser helper = list.getFirst().getUser();
		List<String> labels = list.stream()
				.filter(m -> m.getUser().getId().equals(helper.getId()))
				.map(m -> m.getCapability().getTag().label().toLowerCase())
				.distinct()
				.toList();
		Conversation c = chat.getOrCreate(me, helper, null, null, "Préstamo preventivo · " + rz.getName());
		chat.systemMessage(c, "Préstamo preventivo para " + rz.getName() + ": " + RadarTexts.joinSpanish(labels));
		OffsetDateTime now = OffsetDateTime.now(clock);
		list.stream().filter(m -> m.getUser().getId().equals(helper.getId())).forEach(m -> m.setCoordinatedAt(now));

		Dispatch.Builder d = Dispatch.builder();
		int notified = 0;
		for (AppUser u : users.findByZoneId(rz.getZone().getId())) {
			if (!u.getId().equals(userId) && u.getLat() != null && u.getLng() != null && GeoUtils.haversineMeters(
					u.getLat(), u.getLng(), rz.getCentroidLat(), rz.getCentroidLng()) <= RiskClusteringService.ALERT_RADIUS_M) {
				d.push(u.getId(), "Se coordina ayuda en " + rz.getName(),
						helper.getName() + " presta " + RadarTexts.joinSpanish(labels),
						Map.of("type", "risk_zone", "riskZoneId", riskZoneId.toString()));
				notified++;
			}
		}
		d.push(helper.getId(), me.getName() + " necesita tu ayuda preventiva", rz.getPatternText(),
				Map.of("type", "chat", "conversationId", c.getId().toString()));
		events.publishEvent(d.build());
		return new CoordinateResult(c.getId(), notified);
	}

	@Transactional(readOnly = true)
	public List<HeatSpot> heat(UUID userId) {
		return riskZones.findByZoneIdAndActiveTrueOrderByScoreDesc(zoneOf(userId)).stream()
				.map(z -> new HeatSpot(z.getId(), z.getCentroidLat(), z.getCentroidLng(),
						z.getLevel() == RiskLevel.RED ? 220 : 150, z.getLevel(),
						(z.getLevel() == RiskLevel.RED ? "Riesgo inminente · " : "En vigilancia · ") + z.getName()))
				.toList();
	}

	// ---------------------------------------------------------------- mitigación

	@Transactional(readOnly = true)
	public List<ResourceSummaryDto> mitigationSummary(UUID userId) {
		Map<MitigationType, long[]> totals = new LinkedHashMap<>();
		for (MitigationType t : MitigationType.values()) {
			totals.put(t, new long[2]);
		}
		for (ResourceListing l : listings.findActiveInZone(zoneOf(userId))) {
			if (l.getMitigationType() != null) {
				totals.get(l.getMitigationType())[l.getKind() == ListingKind.HAVE ? 0 : 1] += Math.max(l.getQuantity(), 1);
			}
		}
		List<ResourceSummaryDto> out = new ArrayList<>();
		totals.forEach((t, v) -> out.add(new ResourceSummaryDto(t.name(), t.label(), v[0], v[1])));
		return out;
	}

	@Transactional(readOnly = true)
	public List<ResourceDto> mitigation(UUID userId, MitigationType type, ListingKind kind) {
		AppUser me = user(userId);
		return listings.findActiveInZone(zoneOf(userId)).stream()
				.filter(l -> l.getMitigationType() != null)
				.filter(l -> type == null || l.getMitigationType() == type)
				.filter(l -> kind == null || l.getKind() == kind)
				.map(l -> EmergencyService.mitigationDto(l, me))
				.toList();
	}

	@Transactional
	public ResourceDto createMitigation(UUID userId, MitigationRequest req) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			throw new BusinessRuleException("ZONE_REQUIRED", "Primero guarda tu ubicación.");
		}
		ResourceListing l = listings.save(ResourceListing.builder()
				.user(me)
				.zone(me.getZone())
				.mitigationType(req.mitigationType())
				.kind(req.kind())
				.text(req.text().trim())
				.quantity(req.quantity() != null ? req.quantity() : 1)
				.locationText(req.locationText())
				.lat(me.getLat() != null ? me.getLat() : me.getZone().getCenterLat())
				.lng(me.getLng() != null ? me.getLng() : me.getZone().getCenterLng())
				.build());
		events.publishEvent(new ContentCreatedEvent(SourceType.RESOURCE_LISTING, l.getId()));
		return EmergencyService.mitigationDto(l, me);
	}

	// ---------------------------------------------------------------- utilidades

	RiskZoneDto zoneDto(RiskZone z) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		List<RiskReport> zr = reports.findByRiskZone(z.getId());
		List<RiskCategory> cats = zr.stream().map(RiskReport::getCategory).distinct().toList();
		Map<UUID, MitigationItem> byUser = new LinkedHashMap<>();
		for (MitigationMatch m : matches.findByRiskZoneIdOrderByDistanceM(z.getId())) {
			AppUser u = m.getUser();
			MitigationItem prev = byUser.get(u.getId());
			List<String> labels = new ArrayList<>(prev != null ? prev.labels() : List.of());
			String label = m.getCapability().getTag().label();
			if (!labels.contains(label)) {
				labels.add(label);
			}
			byUser.put(u.getId(), new MitigationItem(u.getId(), u.getName(), labels,
					prev != null ? prev.distanceM() : m.getDistanceM(),
					prev != null ? prev.evidence() : m.getCapability().getEvidenceText(),
					(prev != null && prev.coordinated()) || m.getCoordinatedAt() != null));
		}
		return new RiskZoneDto(z.getId(), z.getName(), z.getLevel(), z.getCentroidLat(), z.getCentroidLng(),
				z.getReportCount(), z.getScore(), z.getPatternText(), z.getHomesEstimate(),
				clustering.alertText(z, cats),
				zr.stream().map(r -> new ZoneReportItem(r.getId(), r.getCategory(), r.getCategory().label(), r.getText(),
						r.getReporter().getName(), Duration.between(r.getCreatedAt(), now).toMinutes(),
						r.getPhotoUrl() != null)).toList(),
				new ArrayList<>(byUser.values()));
	}

	private RiskReportDto toDto(RiskReport r) {
		return new RiskReportDto(r.getId(), r.getCategory(), r.getCategory().label(), r.getText(),
				r.getReporter().getName(), r.getAddressText(), r.getPhotoUrl(), r.getPhotoAnalysis(), r.getSeverityHint(),
				Duration.between(r.getCreatedAt(), OffsetDateTime.now(clock)).toMinutes(),
				r.getRiskZone() != null ? r.getRiskZone().getId() : null, r.getLat(), r.getLng());
	}

	private double[] locate(RiskReportRequest req, List<StreetCode> zoneStreets, AppUser me) {
		if (req.lat() != null && req.lng() != null) {
			return new double[] { req.lat(), req.lng() };
		}
		var code = codec.locationFromAddress(zoneStreets, req.addressText());
		if (code.isPresent()) {
			String prefix = code.get().replaceAll("\\d+$", "");
			var street = zoneStreets.stream().filter(s -> s.getCode().equals(prefix)).findFirst();
			if (street.isPresent()) {
				return new double[] { street.get().getLat(), street.get().getLng() };
			}
		}
		if (me.getLat() != null && me.getLng() != null) {
			return new double[] { me.getLat(), me.getLng() };
		}
		return new double[] { me.getZone().getCenterLat(), me.getZone().getCenterLng() };
	}

	private static byte[] validPhoto(MultipartFile photo) {
		if (photo.getSize() > MAX_PHOTO_BYTES) {
			throw new BusinessRuleException(PHOTO_INVALID, "La foto no debe pasar de 5 MB.");
		}
		if (photo.getContentType() == null || !PHOTO_TYPES.contains(photo.getContentType())) {
			throw new BusinessRuleException(PHOTO_INVALID, "La foto debe ser JPG o PNG.");
		}
		try {
			return photo.getBytes();
		}
		catch (IOException ex) {
			throw new BusinessRuleException(PHOTO_INVALID, "No se pudo leer la foto.");
		}
	}

	private UUID zoneOf(UUID userId) {
		AppUser me = user(userId);
		if (me.getZone() == null) {
			throw new NotFoundException("ZONE_NOT_SET", "Todavía no tienes zona. Guarda tu ubicación primero.");
		}
		return me.getZone().getId();
	}

	private AppUser user(UUID userId) {
		return users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
	}
}
