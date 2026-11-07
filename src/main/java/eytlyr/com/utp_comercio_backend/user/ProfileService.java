package eytlyr.com.utp_comercio_backend.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.ai.Capability;
import eytlyr.com.utp_comercio_backend.ai.CapabilityRepository;
import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.ForbiddenException;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.emergency.ReportStatus;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableReportRepository;
import eytlyr.com.utp_comercio_backend.sos.SosAlertRepository;
import eytlyr.com.utp_comercio_backend.sos.SosStatus;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.AiLearningDto;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.BrigadeMedicalCardDto;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.DeviceTokenRequest;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.LocationRequest;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.MedicalCardDto;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.PrepChecklistItemDto;
import eytlyr.com.utp_comercio_backend.user.dto.ProfileDtos.TrustedContactDto;
import eytlyr.com.utp_comercio_backend.user.dto.UserDto;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneRepository;

/** Perfil del vecino (FASE 4): nombre, ubicación y zona, contactos, ficha médica, ajustes de IA y checklist. */
@Service
public class ProfileService {

	public static final String ZONE_NOT_COVERED = "ZONE_NOT_COVERED";

	public static final String TRUSTED_CONTACTS_MAX = "TRUSTED_CONTACTS_MAX";

	public static final String TRUSTED_CONTACTS_POSITION = "TRUSTED_CONTACTS_POSITION";

	public static final String MEDICAL_CARD_NOT_AVAILABLE = "MEDICAL_CARD_NOT_AVAILABLE";

	static final int MAX_CONTACTS = 3;

	private static final Set<ReportStatus> OPEN_REPORT = Set.of(ReportStatus.PENDING, ReportStatus.ON_THE_WAY);

	private final UserService userService;

	private final ZoneRepository zones;

	private final TrustedContactRepository contacts;

	private final MedicalCardRepository medicalCards;

	private final DeviceTokenRepository deviceTokens;

	private final PrepChecklistItemRepository checklist;

	private final CapabilityRepository capabilities;

	private final SosAlertRepository sosAlerts;

	private final VulnerableReportRepository vulnerableReports;

	public ProfileService(UserService userService, ZoneRepository zones, TrustedContactRepository contacts,
			MedicalCardRepository medicalCards, DeviceTokenRepository deviceTokens, PrepChecklistItemRepository checklist,
			CapabilityRepository capabilities, SosAlertRepository sosAlerts,
			VulnerableReportRepository vulnerableReports) {
		this.userService = userService;
		this.zones = zones;
		this.contacts = contacts;
		this.medicalCards = medicalCards;
		this.deviceTokens = deviceTokens;
		this.checklist = checklist;
		this.capabilities = capabilities;
		this.sosAlerts = sosAlerts;
		this.vulnerableReports = vulnerableReports;
	}

	// ---------------------------------------------------------------- nombre y ubicación

	@Transactional
	public UserDto updateName(UUID userId, String name) {
		AppUser user = userService.get(userId);
		user.setName(name.trim());
		return UserDto.from(user);
	}

	/** Guarda la ubicación y asigna la zona que cubre ese punto o la más cercana si está en fase piloto. */
	@Transactional
	public UserDto updateLocation(UUID userId, LocationRequest req) {
		Zone zone = zones.findAll().stream()
				.map(z -> Map.entry(z, GeoUtils.haversineMeters(req.lat(), req.lng(), z.getCenterLat(), z.getCenterLng())))
				.filter(e -> e.getValue() <= e.getKey().getRadiusM())
				.min(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElseGet(() -> zones.findAll().stream()
						.min(Comparator.comparingDouble(z -> GeoUtils.haversineMeters(req.lat(), req.lng(), z.getCenterLat(), z.getCenterLng())))
						.orElseThrow(() -> new BusinessRuleException(ZONE_NOT_COVERED, "Nearby aún no llega a tu zona")));
		AppUser user = userService.get(userId);
		user.setLat(req.lat());
		user.setLng(req.lng());
		user.setAddressText(blankToNull(req.addressText()));
		user.setZone(zone);
		return UserDto.from(user);
	}

	// ---------------------------------------------------------------- contactos de confianza

	@Transactional(readOnly = true)
	public List<TrustedContactDto> trustedContacts(UUID userId) {
		return contacts.findByUserIdOrderByPosition(userId).stream()
				.map(c -> new TrustedContactDto(c.getName(), c.getPhone(), c.getPosition()))
				.toList();
	}

	/** Reemplaza la lista completa. Sin posición, se usa el orden de la lista. */
	@Transactional
	public List<TrustedContactDto> replaceTrustedContacts(UUID userId, List<TrustedContactDto> list) {
		if (list.size() > MAX_CONTACTS) {
			throw new BusinessRuleException(TRUSTED_CONTACTS_MAX, "Puedes tener hasta 3 contactos de confianza");
		}
		List<TrustedContactDto> normalized = new ArrayList<>();
		Set<Integer> used = new HashSet<>();
		for (int i = 0; i < list.size(); i++) {
			TrustedContactDto c = list.get(i);
			int position = c.position() != null ? c.position() : i + 1;
			if (!used.add(position)) {
				throw new BusinessRuleException(TRUSTED_CONTACTS_POSITION, "Dos contactos no pueden tener la misma posición");
			}
			normalized.add(new TrustedContactDto(c.name().trim(), c.phone(), position));
		}
		contacts.deleteAllByUserId(userId); // limpia el contexto: el usuario se carga después
		AppUser user = userService.get(userId);
		contacts.saveAll(normalized.stream()
				.map(c -> TrustedContact.builder().user(user).name(c.name()).phone(c.phone()).position(c.position()).build())
				.toList());
		normalized.sort(Comparator.comparing(TrustedContactDto::position));
		return normalized;
	}

	// ---------------------------------------------------------------- ficha médica

	@Transactional(readOnly = true)
	public MedicalCardDto medicalCard(UUID userId) {
		return medicalCards.findByUserId(userId).map(ProfileService::toDto)
				.orElse(new MedicalCardDto(null, null, null, List.of()));
	}

	@Transactional
	public MedicalCardDto updateMedicalCard(UUID userId, MedicalCardDto dto) {
		MedicalCard card = medicalCards.findByUserId(userId)
				.orElseGet(() -> MedicalCard.builder().user(userService.get(userId)).build());
		card.setBloodType(blankToNull(dto.bloodType()));
		card.setAllergies(blankToNull(dto.allergies()));
		card.setConditions(blankToNull(dto.conditions()));
		card.setLivesWith(dto.livesWith() == null || dto.livesWith().isEmpty() ? null
				: dto.livesWith().stream().distinct().map(Enum::name).collect(Collectors.joining(",")));
		return toDto(medicalCards.save(card));
	}

	/**
	 * Brigada o admin: solo si la persona tiene un SOS activo o un reporte vulnerable abierto.
	 * El rol ya lo exige el controlador.
	 */
	@Transactional(readOnly = true)
	public BrigadeMedicalCardDto medicalCardForBrigade(UUID personId) {
		AppUser person = userService.get(personId);
		boolean inEmergency = sosAlerts.existsByUserIdAndStatus(personId, SosStatus.ACTIVE)
				|| vulnerableReports.existsByReporterIdAndStatusIn(personId, OPEN_REPORT);
		if (!inEmergency) {
			throw new ForbiddenException(MEDICAL_CARD_NOT_AVAILABLE,
					"La ficha médica solo se puede ver si la persona tiene un SOS activo o un reporte abierto.");
		}
		return new BrigadeMedicalCardDto(person.getId(), person.getName(), person.getPhone(), medicalCard(personId));
	}

	private static MedicalCardDto toDto(MedicalCard c) {
		List<LivesWith> livesWith = c.getLivesWith() == null ? List.of()
				: Arrays.stream(c.getLivesWith().split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.flatMap(s -> {
							try {
								return Stream.of(LivesWith.valueOf(s));
							}
							catch (IllegalArgumentException ex) {
								return Stream.empty(); // valor antiguo o desconocido
							}
						})
						.toList();
		return new MedicalCardDto(c.getBloodType(), c.getAllergies(), c.getConditions(), livesWith);
	}

	// ---------------------------------------------------------------- dispositivo y ajustes

	/** Un token pertenece a un solo teléfono: si ya existía con otro usuario, pasa al actual. */
	@Transactional
	public void registerDeviceToken(UUID userId, DeviceTokenRequest req) {
		AppUser user = userService.get(userId);
		DeviceToken token = deviceTokens.findByToken(req.token())
				.orElseGet(() -> DeviceToken.builder().token(req.token()).build());
		token.setUser(user);
		token.setPlatform(req.platform());
		deviceTokens.save(token);
	}

	/** Si se retira el consentimiento, todo lo aprendido por la IA sale del inventario. */
	@Transactional
	public UserDto updateSettings(UUID userId, boolean aiConsent) {
		AppUser user = userService.get(userId);
		user.setAiConsent(aiConsent);
		if (!aiConsent) {
			capabilities.removeAllByUserId(userId);
			user = userService.get(userId);
		}
		return UserDto.from(user);
	}

	// ---------------------------------------------------------------- aprendizajes de la IA

	@Transactional(readOnly = true)
	public List<AiLearningDto> aiLearnings(UUID userId) {
		return capabilities.findByUserIdAndRemovedByUserFalseOrderByCreatedAtDesc(userId).stream()
				.map(ProfileService::toDto)
				.toList();
	}

	/** Marca el aprendizaje como borrado: la IA no lo vuelve a crear para esa fuente y etiqueta. */
	@Transactional
	public void deleteAiLearning(UUID userId, UUID capabilityId) {
		Capability c = capabilities.findByIdAndUserId(capabilityId, userId)
				.orElseThrow(() -> NotFoundException.of("ese aprendizaje"));
		c.setRemovedByUser(true);
	}

	private static AiLearningDto toDto(Capability c) {
		return new AiLearningDto(c.getId(), c.getTag(), c.getTag().label(), c.getEvidenceText(), c.getSourceType());
	}

	// ---------------------------------------------------------------- "Prepara tu casa"

	/** Siempre devuelve los 4 ítems, en orden, con su estado. */
	@Transactional(readOnly = true)
	public List<PrepChecklistItemDto> prepChecklist(UUID userId) {
		Map<PrepItem, Boolean> done = new EnumMap<>(PrepItem.class);
		for (PrepItem item : PrepItem.values()) {
			done.put(item, false);
		}
		for (PrepChecklistItem row : checklist.findByUserId(userId)) {
			try {
				done.put(PrepItem.valueOf(row.getItemKey()), row.isDone());
			}
			catch (IllegalArgumentException ignored) {
				// Ítem antiguo que ya no existe.
			}
		}
		return done.entrySet().stream().map(e -> new PrepChecklistItemDto(e.getKey(), e.getValue())).toList();
	}

	/** Actualiza solo los ítems enviados. */
	@Transactional
	public List<PrepChecklistItemDto> updatePrepChecklist(UUID userId, List<PrepChecklistItemDto> items) {
		AppUser user = userService.get(userId);
		Map<String, PrepChecklistItem> existing = checklist.findByUserId(userId).stream()
				.collect(Collectors.toMap(PrepChecklistItem::getItemKey, r -> r));
		for (PrepChecklistItemDto item : items) {
			PrepChecklistItem row = existing.computeIfAbsent(item.itemKey().name(),
					key -> PrepChecklistItem.builder().user(user).itemKey(key).build());
			row.setDone(item.done());
			checklist.save(row);
		}
		return prepChecklist(userId);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
