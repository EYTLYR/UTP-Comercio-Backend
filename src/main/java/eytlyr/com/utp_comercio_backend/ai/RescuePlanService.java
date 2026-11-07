package eytlyr.com.utp_comercio_backend.ai;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.AssignmentDto;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.MissionDto;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.NotifyResult;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.RescuePlanDto;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.ZoneEmergencyActivatedEvent;
import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.emergency.Priority;
import eytlyr.com.utp_comercio_backend.emergency.ReportSource;
import eytlyr.com.utp_comercio_backend.emergency.ReportStatus;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableReport;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableReportRepository;
import eytlyr.com.utp_comercio_backend.notification.Dispatch;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneRepository;

/**
 * Protocolo Lázaro, parte 2: en una emergencia cruza el inventario con los reportes.
 * a) Crea reportes preventivos de vecinos vulnerables que nadie reportó.
 * b) Asigna a cada reporte abierto el vecino más cercano con el recurso justo (MISSION_NEEDS del ANEXO B).
 */
@Service
public class RescuePlanService {

	private static final Logger log = LoggerFactory.getLogger(RescuePlanService.class);

	static final int PREVENTIVE_RADIUS_M = 60;

	static final Map<VulnerableCategory, List<CapabilityTag>> MISSION_NEEDS = new EnumMap<>(VulnerableCategory.class);

	static {
		MISSION_NEEDS.put(VulnerableCategory.ELDERLY_ALONE, List.of(CapabilityTag.HEIGHT_RESCUE, CapabilityTag.RESCUE_TOOLS));
		MISSION_NEEDS.put(VulnerableCategory.TRAPPED, List.of(CapabilityTag.HEIGHT_RESCUE, CapabilityTag.RESCUE_TOOLS));
		MISSION_NEEDS.put(VulnerableCategory.INJURED, List.of(CapabilityTag.PARAMEDIC, CapabilityTag.EVAC_TRANSPORT));
		MISSION_NEEDS.put(VulnerableCategory.DISABILITY, List.of(CapabilityTag.EVAC_TRANSPORT, CapabilityTag.HEIGHT_RESCUE));
		MISSION_NEEDS.put(VulnerableCategory.CHILDREN_ALONE, List.of(CapabilityTag.PARAMEDIC));
		MISSION_NEEDS.put(VulnerableCategory.PREGNANT, List.of(CapabilityTag.PARAMEDIC, CapabilityTag.EVAC_TRANSPORT));
		MISSION_NEEDS.put(VulnerableCategory.OXYGEN_DEPENDENT, List.of(CapabilityTag.BACKUP_POWER, CapabilityTag.PARAMEDIC));
	}

	private static final Set<ReportStatus> OPEN = EnumSet.of(ReportStatus.PENDING, ReportStatus.ON_THE_WAY);

	private final CapabilityRepository capabilities;

	private final VulnerableReportRepository reports;

	private final RescuePlanRepository plans;

	private final MissionAssignmentRepository assignments;

	private final ZoneRepository zones;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final boolean autoPlan;

	public RescuePlanService(CapabilityRepository capabilities, VulnerableReportRepository reports,
			RescuePlanRepository plans, MissionAssignmentRepository assignments, ZoneRepository zones,
			ApplicationEventPublisher events, Clock clock, NearbyProperties props) {
		this.capabilities = capabilities;
		this.reports = reports;
		this.plans = plans;
		this.assignments = assignments;
		this.zones = zones;
		this.events = events;
		this.clock = clock;
		this.autoPlan = props.ai().autoRescuePlan();
	}

	/** Al pasar la zona a EMERGENCY se genera el plan solo, en segundo plano. */
	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onEmergency(ZoneEmergencyActivatedEvent event) {
		if (autoPlan) {
			RescuePlanDto plan = generate(event.zoneId());
			log.warn("Plan de rescate automático: {} misiones y {} reportes preventivos en {} ms", plan.missions().size(),
					plan.preventiveReports(), plan.durationMs());
		}
	}

	@Transactional
	public RescuePlanDto generate(UUID zoneId) {
		long start = System.nanoTime();
		Zone zone = zones.findById(zoneId).orElseThrow(() -> NotFoundException.of("la zona"));
		OffsetDateTime now = OffsetDateTime.now(clock);
		List<Capability> inventory = capabilities.findActiveInZone(zoneId);
		List<VulnerableReport> open = new ArrayList<>(reports.findInZone(zoneId, OPEN));

		// a) Reportes preventivos: un vecino vulnerable (una vez por persona) sin reporte abierto a menos de 60 m.
		int preventive = 0;
		Map<UUID, Capability> byPerson = new LinkedHashMap<>();
		inventory.stream()
				.filter(c -> c.getKind() == CapabilityKind.VULNERABILITY && c.getUser() != null)
				.sorted(Comparator.comparing(Capability::getTag))
				.forEach(c -> byPerson.putIfAbsent(c.getUser().getId(), c));
		for (Capability c : byPerson.values()) {
			boolean covered = open.stream().anyMatch(r -> meters(r.getLat(), r.getLng(), c.getLat(), c.getLng())
					< PREVENTIVE_RADIUS_M);
			if (covered || reports.existsBySourceCapabilityId(c.getId())) {
				continue;
			}
			VulnerableReport r = reports.save(VulnerableReport.builder()
					.zone(zone)
					.category(categoryFor(c.getTag()))
					.priority(Priority.HIGH)
					.status(ReportStatus.PENDING)
					.source(ReportSource.AI_PREVENTIVE)
					.detail("Nadie lo ha reportado. La IA lo sabe por su " + AiText.sourceName(c.getSourceType()) + " ("
							+ AiText.ago(c.getCreatedAt(), now) + "): «" + c.getEvidenceText() + "».")
					.lat(c.getLat())
					.lng(c.getLng())
					.addressText(c.getZoneLabel())
					.sourceCapability(c)
					.build());
			open.add(r);
			preventive++;
		}

		// b) Misiones: por cada necesidad, la capacidad más cercana sin repetir persona en la misma misión.
		RescuePlan plan = plans.save(RescuePlan.builder().zone(zone).generatedAt(now).durationMs(0).build());
		List<MissionDto> missions = new ArrayList<>();
		int assigned = 0;
		for (VulnerableReport r : open) {
			Set<UUID> used = new HashSet<>();
			if (r.getSourceCapability() != null && r.getSourceCapability().getUser() != null) {
				used.add(r.getSourceCapability().getUser().getId()); // la propia persona vulnerable no se asigna
			}
			List<AssignmentDto> list = new ArrayList<>();
			for (CapabilityTag need : MISSION_NEEDS.getOrDefault(r.getCategory(), List.of())) {
				Capability best = inventory.stream()
						.filter(c -> c.getTag() == need && c.getKind() != CapabilityKind.VULNERABILITY)
						.filter(c -> c.getUser() != null && !used.contains(c.getUser().getId()))
						.min(Comparator.comparingDouble(c -> meters(r.getLat(), r.getLng(), c.getLat(), c.getLng())))
						.orElse(null);
				if (best == null) {
					continue;
				}
				used.add(best.getUser().getId());
				int distance = (int) Math.round(meters(r.getLat(), r.getLng(), best.getLat(), best.getLng()));
				String reason = capitalize(AiText.sourceName(best.getSourceType())) + " "
						+ AiText.ago(best.getCreatedAt(), now) + ": «" + best.getEvidenceText() + "»";
				assignments.save(MissionAssignment.builder()
						.plan(plan)
						.report(r)
						.capability(best)
						.user(best.getUser())
						.distanceM(distance)
						.reason(reason)
						.build());
				list.add(new AssignmentDto(best.getUser().getId(), best.getUser().getName(), best.getTag(),
						best.getTag().label(), distance, reason));
				assigned++;
			}
			missions.add(new MissionDto(r.getId(), r.getCategory(), summary(r), list));
		}
		long durationMs = Math.max(0, (System.nanoTime() - start) / 1_000_000);
		plan.setDurationMs(durationMs);
		plan.setMissionsCount(assigned);
		plan.setPreventiveReportsCount(preventive);
		return new RescuePlanDto(plan.getId(), now, durationMs, missions, preventive);
	}

	@Transactional(readOnly = true)
	public RescuePlanDto latest(UUID zoneId) {
		RescuePlan plan = plans.findFirstByZoneIdOrderByGeneratedAtDesc(zoneId)
				.orElseThrow(() -> new NotFoundException("RESCUE_PLAN_NOT_FOUND", "Todavía no hay un plan de rescate."));
		Map<UUID, MissionDto> missions = new LinkedHashMap<>();
		for (MissionAssignment a : assignments.findByPlanIdOrderByCreatedAt(plan.getId())) {
			VulnerableReport r = a.getReport();
			missions.computeIfAbsent(r.getId(), id -> new MissionDto(id, r.getCategory(), summary(r), new ArrayList<>()))
					.assignments()
					.add(new AssignmentDto(a.getUser().getId(), a.getUser().getName(), a.getCapability().getTag(),
							a.getCapability().getTag().label(), a.getDistanceM(), a.getReason()));
		}
		return new RescuePlanDto(plan.getId(), plan.getGeneratedAt(), plan.getDurationMs(),
				new ArrayList<>(missions.values()), plan.getPreventiveReportsCount());
	}

	/** Avisa a los asignados y deja el reporte "en camino" con sus nombres ("Jorge y Pedro"). */
	@Transactional
	public NotifyResult notifyMission(UUID reportId) {
		VulnerableReport r = reports.findById(reportId).orElseThrow(() -> NotFoundException.of("el reporte"));
		RescuePlan plan = plans.findFirstByZoneIdOrderByGeneratedAtDesc(r.getZone().getId())
				.orElseThrow(() -> new NotFoundException("RESCUE_PLAN_NOT_FOUND", "Todavía no hay un plan de rescate."));
		List<MissionAssignment> list = assignments.findByPlanIdAndReportIdOrderByCreatedAt(plan.getId(), reportId);
		if (list.isEmpty()) {
			throw new BusinessRuleException("MISSION_EMPTY", "La IA no encontró vecinos con los recursos para este reporte.");
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		Dispatch.Builder d = Dispatch.builder();
		List<String> names = new ArrayList<>();
		for (MissionAssignment a : list) {
			a.setNotifiedAt(now);
			a.setStatus(MissionStatus.NOTIFIED);
			names.add(a.getUser().getName().split("\\s+")[0]);
			d.push(a.getUser().getId(), "Te necesitan a " + a.getDistanceM() + " m: " + r.getCategory().label(),
					"Llevas: " + a.getCapability().getTag().label(),
					Map.of("type", "mission", "reportId", reportId.toString()));
		}
		String label = names.size() == 1 ? names.getFirst()
				: String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.getLast();
		r.setStatus(ReportStatus.ON_THE_WAY);
		r.setAssignedTo(list.getFirst().getUser());
		r.setAssignedLabel(label);
		events.publishEvent(d.build());
		return new NotifyResult(reportId, label, list.size());
	}

	static VulnerableCategory categoryFor(CapabilityTag tag) {
		return switch (tag) {
			case REDUCED_MOBILITY -> VulnerableCategory.DISABILITY;
			case OXYGEN_DEPENDENT -> VulnerableCategory.OXYGEN_DEPENDENT;
			default -> VulnerableCategory.ELDERLY_ALONE;
		};
	}

	private static String summary(VulnerableReport r) {
		return r.getCategory().label() + (r.getAddressText() != null ? " · " + r.getAddressText() : "");
	}

	private static double meters(double lat1, double lng1, double lat2, double lng2) {
		return GeoUtils.haversineMeters(lat1, lng1, lat2, lng2);
	}

	private static String capitalize(String s) {
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
