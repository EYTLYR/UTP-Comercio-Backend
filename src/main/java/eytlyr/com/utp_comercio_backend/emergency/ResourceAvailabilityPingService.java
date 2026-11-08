package eytlyr.com.utp_comercio_backend.emergency;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.ConflictException;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.emergency.dto.ResourceAvailabilityDtos.AvailabilityPingResponseDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.ResourceAvailabilityDtos.CandidatePingDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.ResourceAvailabilityDtos.CreateAvailabilityPingRequest;
import eytlyr.com.utp_comercio_backend.notification.FcmNotificationService;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

@Service
public class ResourceAvailabilityPingService {

	private static final Logger log = LoggerFactory.getLogger(ResourceAvailabilityPingService.class);

	private final ResourceRequestRepository requestRepo;
	private final ResourceRequestCandidateRepository candidateRepo;
	private final ResourceListingRepository listingRepo;
	private final AppUserRepository userRepo;
	private final FcmNotificationService fcmService;

	public ResourceAvailabilityPingService(
			ResourceRequestRepository requestRepo,
			ResourceRequestCandidateRepository candidateRepo,
			ResourceListingRepository listingRepo,
			AppUserRepository userRepo,
			FcmNotificationService fcmService) {
		this.requestRepo = requestRepo;
		this.candidateRepo = candidateRepo;
		this.listingRepo = listingRepo;
		this.userRepo = userRepo;
		this.fcmService = fcmService;
	}

	/**
	 * Inicia un "Ping de Disponibilidad": busca a los 3 proveedores más cercanos del recurso
	 * y les despacha una notificación push simultánea sin bloquear el hilo.
	 */
	@Transactional
	public AvailabilityPingResponseDto createPing(UUID requesterId, CreateAvailabilityPingRequest req) {
		if (req.resourceType() == null && req.mitigationType() == null) {
			throw new BusinessRuleException("RESOURCE_REQUIRED", "Debes especificar un tipo de recurso o mitigación.");
		}

		AppUser me = userRepo.findById(requesterId)
				.orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuario no encontrado"));

		double lat = req.lat() != null ? req.lat()
				: (me.getLat() != null ? me.getLat() : (me.getZone() != null ? me.getZone().getCenterLat() : 0.0));
		double lng = req.lng() != null ? req.lng()
				: (me.getLng() != null ? me.getLng() : (me.getZone() != null ? me.getZone().getCenterLng() : 0.0));

		String resTypeStr = req.resourceType() != null ? req.resourceType().name() : null;
		String mitTypeStr = req.mitigationType() != null ? req.mitigationType().name() : null;

		List<ResourceListing> top3Providers = listingRepo.findTop3ClosestProviders(
				requesterId, resTypeStr, mitTypeStr, lat, lng);

		if (top3Providers.isEmpty()) {
			throw new NotFoundException("NO_PROVIDERS_AVAILABLE",
					"No se encontraron vecinos cercanos que tengan este recurso disponible.");
		}

		ResourceRequest request = ResourceRequest.builder()
				.requester(me)
				.resourceType(req.resourceType())
				.mitigationType(req.mitigationType())
				.status(ResourceRequestStatus.PENDING)
				.lat(lat)
				.lng(lng)
				.note(req.note() != null ? req.note().trim() : null)
				.build();

		for (ResourceListing listing : top3Providers) {
			ResourceRequestCandidate candidate = ResourceRequestCandidate.builder()
					.request(request)
					.provider(listing.getUser())
					.listing(listing)
					.status(CandidateStatus.PENDING)
					.notifiedAt(OffsetDateTime.now())
					.build();
			request.getCandidates().add(candidate);
		}

		ResourceRequest saved = requestRepo.save(request);

		// Disparo simultáneo de notificación push a los 3 vecinos más cercanos
		String label = resourceLabel(saved);
		String pushTitle = "Disponibilidad de recurso: " + label;
		String pushBody = me.getName() + " necesita \"" + label + "\". ¿Puedes prestarlo ahora?";
		Map<String, String> payload = Map.of(
				"type", "RESOURCE_PING",
				"requestId", saved.getId().toString(),
				"resourceLabel", label
		);

		for (ResourceRequestCandidate c : saved.getCandidates()) {
			fcmService.notifyUserAsync(c.getProvider().getId(), pushTitle, pushBody, payload);
		}

		log.info("Ping de disponibilidad creado (id={}) con {} vecinos notificados simultáneamente",
				saved.getId(), saved.getCandidates().size());

		return toDto(saved);
	}

	/**
	 * El primer proveedor que presione "Sí, la presto" cambia el ResourceRequest a ACCEPTED.
	 * Inmediatamente cancela y oculta las solicitudes en las pantallas de los otros vecinos.
	 */
	@Transactional
	public AvailabilityPingResponseDto acceptRequest(UUID providerId, UUID requestId) {
		ResourceRequestCandidate candidate = candidateRepo.findByRequestIdAndProviderId(requestId, providerId)
				.orElseThrow(() -> new BusinessRuleException("NOT_A_CANDIDATE",
						"No eres uno de los vecinos seleccionados para esta solicitud."));

		// Actualización atómica en base de datos: solo triunfa si status sigue PENDING
		int updated = requestRepo.acceptRequestAtomic(requestId, providerId, candidate.getListing().getId());
		if (updated == 0) {
			throw new ConflictException("REQUEST_ALREADY_ACCEPTED",
					"Esta solicitud ya fue aceptada por otro vecino o ya no se encuentra disponible.");
		}

		// Marcamos al ganador como ACCEPTED
		candidate.setStatus(CandidateStatus.ACCEPTED);
		candidate.setRespondedAt(OffsetDateTime.now());
		candidateRepo.save(candidate);

		// Inmediatamente cancelamos a los otros vecinos candidatos en BD para ocultarles la solicitud
		candidateRepo.cancelOtherCandidates(requestId, providerId);

		ResourceRequest request = requestRepo.findByIdWithDetails(requestId)
				.orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Solicitud no encontrada"));

		// Notificamos simultáneamente a los otros vecinos para que sus apps oculten la tarjeta de ping
		List<ResourceRequestCandidate> candidates = candidateRepo.findByRequestId(requestId);
		Map<String, String> cancelPayload = Map.of(
				"type", "RESOURCE_REQUEST_CANCELLED",
				"requestId", requestId.toString()
		);
		for (ResourceRequestCandidate c : candidates) {
			if (!c.getProvider().getId().equals(providerId)) {
				fcmService.notifyUserAsync(
						c.getProvider().getId(),
						"Solicitud completada",
						"Otro vecino ya facilitó este recurso. ¡Gracias por tu buena disposición!",
						cancelPayload
				);
			}
		}

		// Notificamos al solicitante que el recurso fue aceptado
		fcmService.notifyUserAsync(
				request.getRequester().getId(),
				"¡Recurso en camino!",
				candidate.getProvider().getName() + " aceptó prestarte el recurso.",
				Map.of(
						"type", "RESOURCE_REQUEST_ACCEPTED",
						"requestId", requestId.toString(),
						"providerId", providerId.toString()
				)
		);

		log.info("Solicitud {} aceptada por el proveedor {}. Otros vecinos cancelados.", requestId, providerId);

		return toDto(request);
	}

	/**
	 * Devuelve únicamente las solicitudes donde este vecino es candidato y que sigan PENDING.
	 * Las solicitudes aceptadas por otros vecinos se ocultan automáticamente.
	 */
	@Transactional(readOnly = true)
	public List<CandidatePingDto> getPendingPingsForProvider(UUID providerId) {
		return candidateRepo.findPendingByProviderId(providerId).stream()
				.map(c -> new CandidatePingDto(
						c.getRequest().getId(),
						c.getId(),
						c.getRequest().getRequester().getName(),
						resourceLabel(c.getRequest()),
						c.getRequest().getNote(),
						c.getRequest().getLat(),
						c.getRequest().getLng(),
						c.getStatus(),
						c.getNotifiedAt()
				))
				.toList();
	}

	@Transactional(readOnly = true)
	public AvailabilityPingResponseDto getRequest(UUID requestId) {
		ResourceRequest r = requestRepo.findByIdWithDetails(requestId)
				.orElseThrow(() -> new NotFoundException("REQUEST_NOT_FOUND", "Solicitud no encontrada"));
		return toDto(r);
	}

	private String resourceLabel(ResourceRequest r) {
		if (r.getResourceType() != null) {
			return r.getResourceType().label();
		}
		if (r.getMitigationType() != null) {
			return r.getMitigationType().label();
		}
		return "Recurso";
	}

	private AvailabilityPingResponseDto toDto(ResourceRequest r) {
		String resType = r.getResourceType() != null ? r.getResourceType().name() : null;
		String mitType = r.getMitigationType() != null ? r.getMitigationType().name() : null;
		UUID acceptedById = r.getAcceptedBy() != null ? r.getAcceptedBy().getId() : null;
		String acceptedByName = r.getAcceptedBy() != null ? r.getAcceptedBy().getName() : null;

		return new AvailabilityPingResponseDto(
				r.getId(),
				r.getRequester().getId(),
				r.getRequester().getName(),
				resType,
				mitType,
				resourceLabel(r),
				r.getStatus(),
				r.getLat(),
				r.getLng(),
				r.getNote(),
				acceptedById,
				acceptedByName,
				r.getCandidates().size(),
				r.getCreatedAt()
		);
	}

}
