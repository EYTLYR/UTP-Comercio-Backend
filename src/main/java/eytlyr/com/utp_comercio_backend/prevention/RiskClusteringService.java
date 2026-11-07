package eytlyr.com.utp_comercio_backend.prevention;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.ai.Capability;
import eytlyr.com.utp_comercio_backend.ai.CapabilityRepository;
import eytlyr.com.utp_comercio_backend.ai.CapabilityTag;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.emergency.EmergencyService;
import eytlyr.com.utp_comercio_backend.notification.Dispatch;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;
import eytlyr.com.utp_comercio_backend.zone.StreetCode;
import eytlyr.com.utp_comercio_backend.zone.StreetCodeRepository;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneRepository;

/**
 * Radar preventivo (El Niño): agrupa los reportes de riesgo cercanos, calcula el nivel (AMBER o RED),
 * explica el patrón y busca en el inventario los recursos de mitigación más cercanos.
 */
@Service
public class RiskClusteringService {

	public record Recompute(List<RiskZone> zones, Set<UUID> escalated, long durationMs) {
	}

	static final int ALERT_RADIUS_M = 300;

	static final EnumSet<CapabilityTag> MITIGATION_TAGS = EnumSet.of(CapabilityTag.SANDBAGS, CapabilityTag.PUMP,
			CapabilityTag.ROOF_COVER, CapabilityTag.SHOVELS, CapabilityTag.EVAC_TRANSPORT);

	private final RiskReportRepository reports;

	private final RiskZoneRepository riskZones;

	private final MitigationMatchRepository matches;

	private final CapabilityRepository capabilities;

	private final StreetCodeRepository streets;

	private final ZoneRepository zones;

	private final AppUserRepository users;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final NearbyProperties.Prevention props;

	public RiskClusteringService(RiskReportRepository reports, RiskZoneRepository riskZones,
			MitigationMatchRepository matches, CapabilityRepository capabilities, StreetCodeRepository streets,
			ZoneRepository zones, AppUserRepository users, ApplicationEventPublisher events, Clock clock,
			NearbyProperties props) {
		this.reports = reports;
		this.riskZones = riskZones;
		this.matches = matches;
		this.capabilities = capabilities;
		this.streets = streets;
		this.zones = zones;
		this.users = users;
		this.events = events;
		this.clock = clock;
		this.props = props.prevention();
	}

	@Transactional
	public Recompute recompute(UUID zoneId) {
		long start = System.nanoTime();
		Zone zone = zones.findById(zoneId).orElseThrow(() -> NotFoundException.of("la zona"));
		OffsetDateTime now = OffsetDateTime.now(clock);
		List<RiskReport> recent = reports.findRecentInZone(zoneId, now.minusHours(props.clusterWindowHours()));
		List<List<RiskReport>> groups = cluster(recent, props.clusterRadiusM());
		List<StreetCode> zoneStreets = streets.findByZoneId(zoneId);
		List<Capability> inventory = capabilities.findByZoneIdAndTagInAndRemovedByUserFalse(zoneId, MITIGATION_TAGS);
		List<RiskZone> existing = new ArrayList<>(riskZones.findByZoneIdAndActiveTrueOrderByScoreDesc(zoneId));

		recent.forEach(r -> r.setRiskZone(null));
		List<RiskZone> result = new ArrayList<>();
		Set<UUID> escalated = new HashSet<>();
		for (List<RiskReport> group : groups) {
			double lat = group.stream().mapToDouble(RiskReport::getLat).average().orElseThrow();
			double lng = group.stream().mapToDouble(RiskReport::getLng).average().orElseThrow();
			List<RiskCategory> cats = group.stream().map(RiskReport::getCategory).distinct().toList();
			double score = group.stream().mapToDouble(r -> RadarTexts.WEIGHTS.get(r.getCategory())).sum();
			RiskLevel level = group.size() >= 3 || score >= 4 ? RiskLevel.RED : RiskLevel.AMBER;
			String name = streetName(group, zoneStreets, lat, lng);

			RiskZone rz = existing.stream()
					.filter(z -> GeoUtils.haversineMeters(z.getCentroidLat(), z.getCentroidLng(), lat, lng) <= props.clusterRadiusM())
					.findFirst()
					.orElse(null);
			boolean isNew = rz == null;
			RiskLevel previous = isNew ? null : rz.getLevel();
			if (isNew) {
				rz = RiskZone.builder().zone(zone).build();
			}
			else {
				existing.remove(rz);
			}
			rz.setName(name);
			rz.setLevel(level);
			rz.setCentroidLat(lat);
			rz.setCentroidLng(lng);
			rz.setReportCount(group.size());
			rz.setScore(score);
			rz.setPatternText(RadarTexts.pattern(group.size(), name, cats));
			rz.setHomesEstimate(6 + 4 * group.size());
			rz.setActive(true);
			riskZones.save(rz);
			RiskZone saved = rz;
			group.forEach(r -> r.setRiskZone(saved));
			refreshMitigation(rz, cats, inventory);
			result.add(rz);
			if (level == RiskLevel.RED && (isNew || previous == RiskLevel.AMBER)) {
				escalated.add(rz.getId());
				alertNeighbors(rz, cats);
			}
		}
		existing.forEach(z -> z.setActive(false)); // zonas que ya no tienen grupo
		result.sort(Comparator.comparing(RiskZone::getScore).reversed());
		return new Recompute(result, escalated, Math.max(0, (System.nanoTime() - start) / 1_000_000));
	}

	/** Texto de alerta con el primer vecino de la mitigación sugerida. */
	@Transactional(readOnly = true)
	public String alertText(RiskZone rz, List<RiskCategory> cats) {
		List<MitigationMatch> list = matches.findByRiskZoneIdOrderByDistanceM(rz.getId());
		if (list.isEmpty()) {
			return RadarTexts.alert(RadarTexts.riskName(cats), rz.getName(), null, List.of(), null,
					cats.contains(RiskCategory.RAVINE_ACTIVITY));
		}
		AppUser first = list.getFirst().getUser();
		List<MitigationMatch> his = list.stream().filter(m -> m.getUser().getId().equals(first.getId())).toList();
		return RadarTexts.alert(RadarTexts.riskName(cats), rz.getName(), first.getName(),
				his.stream().map(m -> m.getCapability().getTag()).distinct().sorted().toList(), list.getFirst().getDistanceM(),
				cats.contains(RiskCategory.RAVINE_ACTIVITY));
	}

	// ---------------------------------------------------------------- internos

	/** Enlace simple: dos reportes a 150 m o menos van al mismo grupo. Solo grupos de 2 o más. */
	static List<List<RiskReport>> cluster(List<RiskReport> list, int radiusM) {
		int n = list.size();
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) {
			parent[i] = i;
		}
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				RiskReport a = list.get(i);
				RiskReport b = list.get(j);
				if (GeoUtils.haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng()) <= radiusM) {
					parent[find(parent, i)] = find(parent, j);
				}
			}
		}
		Map<Integer, List<RiskReport>> groups = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(list.get(i));
		}
		return groups.values().stream().filter(g -> g.size() >= 2).toList();
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	/** Calle más común en las direcciones (sin número ni paréntesis); si no hay, la más cercana al centro. */
	static String streetName(List<RiskReport> group, List<StreetCode> streets, double lat, double lng) {
		Map<String, Long> counts = group.stream()
				.map(r -> r.getAddressText() == null ? null
						: EmergencyService.coarseAddress(r.getAddressText().replaceAll("\\s*\\(.*?\\)", "")))
				.filter(s -> s != null && !s.isBlank())
				.collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
		return counts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElseGet(() -> streets.stream()
						.min(Comparator.comparingDouble(s -> GeoUtils.haversineMeters(lat, lng, s.getLat(), s.getLng())))
						.map(StreetCode::getName)
						.orElse("la zona"));
	}

	/**
	 * Por cada necesidad de MITIGATION_NEEDS, la capacidad más cercana del inventario. Se conserva
	 * "coordinado" si ya se había coordinado con esa persona.
	 */
	private void refreshMitigation(RiskZone rz, List<RiskCategory> cats, List<Capability> inventory) {
		Map<UUID, OffsetDateTime> coordinated = new HashMap<>();
		if (rz.getId() != null) {
			matches.findByRiskZoneIdOrderByDistanceM(rz.getId()).stream()
					.filter(m -> m.getCoordinatedAt() != null)
					.forEach(m -> coordinated.put(m.getUser().getId(), m.getCoordinatedAt()));
			matches.deleteByRiskZone(rz.getId());
		}
		Set<CapabilityTag> needs = new LinkedHashSet<>();
		cats.forEach(c -> needs.addAll(RadarTexts.MITIGATION_NEEDS.get(c)));
		for (CapabilityTag need : needs) {
			inventory.stream()
					.filter(c -> c.getTag() == need && c.getUser() != null)
					.min(Comparator.comparingDouble(c -> GeoUtils.haversineMeters(rz.getCentroidLat(), rz.getCentroidLng(),
							c.getLat(), c.getLng())))
					.ifPresent(c -> matches.save(MitigationMatch.builder()
							.riskZone(rz)
							.capability(c)
							.user(c.getUser())
							.distanceM((int) Math.round(GeoUtils.haversineMeters(rz.getCentroidLat(), rz.getCentroidLng(),
									c.getLat(), c.getLng())))
							.coordinatedAt(coordinated.get(c.getUser().getId()))
							.build()));
		}
	}

	/** La zona subió a RED: aviso a las viviendas a 300 m con la mitigación sugerida. */
	private void alertNeighbors(RiskZone rz, List<RiskCategory> cats) {
		String text = alertText(rz, cats);
		Dispatch.Builder d = Dispatch.builder();
		for (AppUser u : users.findByZoneId(rz.getZone().getId())) {
			if (u.getLat() != null && u.getLng() != null
					&& GeoUtils.haversineMeters(u.getLat(), u.getLng(), rz.getCentroidLat(), rz.getCentroidLng()) <= ALERT_RADIUS_M) {
				d.push(u.getId(), "Riesgo inminente · " + rz.getName(), text,
						Map.of("type", "risk_zone", "riskZoneId", rz.getId().toString()));
			}
		}
		events.publishEvent(d.build());
	}
}
