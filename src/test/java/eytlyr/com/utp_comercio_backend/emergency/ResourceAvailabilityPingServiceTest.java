package eytlyr.com.utp_comercio_backend.emergency;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import eytlyr.com.utp_comercio_backend.common.ConflictException;
import eytlyr.com.utp_comercio_backend.emergency.dto.ResourceAvailabilityDtos.AvailabilityPingResponseDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.ResourceAvailabilityDtos.CandidatePingDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.ResourceAvailabilityDtos.CreateAvailabilityPingRequest;
import eytlyr.com.utp_comercio_backend.notification.FcmNotificationService;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAvailabilityPingServiceTest {

	@Mock
	private ResourceRequestRepository requestRepo;

	@Mock
	private ResourceRequestCandidateRepository candidateRepo;

	@Mock
	private ResourceListingRepository listingRepo;

	@Mock
	private AppUserRepository userRepo;

	@Mock
	private FcmNotificationService fcmService;

	@InjectMocks
	private ResourceAvailabilityPingService service;

	private AppUser requester;
	private AppUser provider1;
	private AppUser provider2;
	private AppUser provider3;
	private ResourceListing listing1;
	private ResourceListing listing2;
	private ResourceListing listing3;

	@BeforeEach
	void setUp() {
		requester = AppUser.builder().name("Juan Solicitante").lat(-12.046).lng(-77.042).build();
		requester.setId(UUID.randomUUID());

		provider1 = AppUser.builder().name("Vecino Carlos").lat(-12.047).lng(-77.043).build();
		provider1.setId(UUID.randomUUID());

		provider2 = AppUser.builder().name("Vecina Ana").lat(-12.048).lng(-77.044).build();
		provider2.setId(UUID.randomUUID());

		provider3 = AppUser.builder().name("Vecino Pedro").lat(-12.049).lng(-77.045).build();
		provider3.setId(UUID.randomUUID());

		listing1 = ResourceListing.builder().user(provider1).mitigationType(MitigationType.PUMP).build();
		listing1.setId(UUID.randomUUID());

		listing2 = ResourceListing.builder().user(provider2).mitigationType(MitigationType.PUMP).build();
		listing2.setId(UUID.randomUUID());

		listing3 = ResourceListing.builder().user(provider3).mitigationType(MitigationType.PUMP).build();
		listing3.setId(UUID.randomUUID());
	}

	@Test
	@DisplayName("Crea solicitud en PENDING y envía notificaciones push simultáneas a los 3 proveedores más cercanos")
	void createPing_success() {
		when(userRepo.findById(requester.getId())).thenReturn(Optional.of(requester));
		when(listingRepo.findTop3ClosestProviders(eq(requester.getId()), any(), eq("PUMP"), eq(-12.046), eq(-77.042)))
				.thenReturn(List.of(listing1, listing2, listing3));

		when(requestRepo.save(any(ResourceRequest.class))).thenAnswer(invocation -> {
			ResourceRequest r = invocation.getArgument(0);
			r.setId(UUID.randomUUID());
			return r;
		});

		CreateAvailabilityPingRequest req = new CreateAvailabilityPingRequest(
				null, MitigationType.PUMP, -12.046, -77.042, "Inundación en el primer piso");

		AvailabilityPingResponseDto response = service.createPing(requester.getId(), req);

		assertThat(response.status()).isEqualTo(ResourceRequestStatus.PENDING);
		assertThat(response.candidatesCount()).isEqualTo(3);
		assertThat(response.resourceLabel()).isEqualTo("Motobombas");

		// Verifica que se enviaron 3 notificaciones push con FcmNotificationService
		verify(fcmService, times(3)).notifyUserAsync(any(UUID.class), any(), any(), any());
	}

	@Test
	@DisplayName("El primer proveedor que acepta cambia el estado a ACCEPTED y cancela a los demás")
	void acceptRequest_firstProviderWins() {
		UUID requestId = UUID.randomUUID();

		ResourceRequest request = ResourceRequest.builder()
				.requester(requester)
				.mitigationType(MitigationType.PUMP)
				.status(ResourceRequestStatus.PENDING)
				.build();
		request.setId(requestId);

		ResourceRequestCandidate c1 = ResourceRequestCandidate.builder()
				.request(request).provider(provider1).listing(listing1).status(CandidateStatus.PENDING).build();
		ResourceRequestCandidate c2 = ResourceRequestCandidate.builder()
				.request(request).provider(provider2).listing(listing2).status(CandidateStatus.PENDING).build();
		ResourceRequestCandidate c3 = ResourceRequestCandidate.builder()
				.request(request).provider(provider3).listing(listing3).status(CandidateStatus.PENDING).build();

		when(candidateRepo.findByRequestIdAndProviderId(requestId, provider1.getId())).thenReturn(Optional.of(c1));
		when(requestRepo.acceptRequestAtomic(requestId, provider1.getId(), listing1.getId())).thenReturn(1);

		request.setStatus(ResourceRequestStatus.ACCEPTED);
		request.setAcceptedBy(provider1);
		request.setAcceptedListing(listing1);
		when(requestRepo.findByIdWithDetails(requestId)).thenReturn(Optional.of(request));
		when(candidateRepo.findByRequestId(requestId)).thenReturn(List.of(c1, c2, c3));

		AvailabilityPingResponseDto result = service.acceptRequest(provider1.getId(), requestId);

		assertThat(result.status()).isEqualTo(ResourceRequestStatus.ACCEPTED);
		assertThat(result.acceptedById()).isEqualTo(provider1.getId());
		assertThat(c1.getStatus()).isEqualTo(CandidateStatus.ACCEPTED);

		// Verifica que se cancelaron los otros candidatos en base de datos
		verify(candidateRepo).cancelOtherCandidates(requestId, provider1.getId());

		// Verifica push de cancelación a los otros 2 vecinos (provider2 y provider3)
		verify(fcmService).notifyUserAsync(eq(provider2.getId()), eq("Solicitud completada"), any(), any());
		verify(fcmService).notifyUserAsync(eq(provider3.getId()), eq("Solicitud completada"), any(), any());

		// Verifica push de confirmación al solicitante
		verify(fcmService).notifyUserAsync(eq(requester.getId()), eq("¡Recurso en camino!"), any(), any());
	}

	@Test
	@DisplayName("Si otro proveedor intenta aceptar después, recibe 409 Conflict")
	void acceptRequest_secondProviderGetsConflict() {
		UUID requestId = UUID.randomUUID();

		ResourceRequestCandidate c2 = ResourceRequestCandidate.builder()
				.provider(provider2).listing(listing2).status(CandidateStatus.PENDING).build();

		when(candidateRepo.findByRequestIdAndProviderId(requestId, provider2.getId())).thenReturn(Optional.of(c2));
		// Retorna 0 porque ya no está PENDING
		when(requestRepo.acceptRequestAtomic(requestId, provider2.getId(), listing2.getId())).thenReturn(0);

		assertThatThrownBy(() -> service.acceptRequest(provider2.getId(), requestId))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("ya fue aceptada por otro vecino");
	}

	@Test
	@DisplayName("Las solicitudes pendientes de un proveedor solo retornan las activas en PENDING")
	void getPendingPingsForProvider_onlyPending() {
		ResourceRequest req = ResourceRequest.builder()
				.requester(requester)
				.mitigationType(MitigationType.PUMP)
				.status(ResourceRequestStatus.PENDING)
				.note("Urgente")
				.build();
		req.setId(UUID.randomUUID());

		ResourceRequestCandidate candidate = ResourceRequestCandidate.builder()
				.request(req)
				.provider(provider1)
				.listing(listing1)
				.status(CandidateStatus.PENDING)
				.notifiedAt(OffsetDateTime.now())
				.build();
		candidate.setId(UUID.randomUUID());

		when(candidateRepo.findPendingByProviderId(provider1.getId())).thenReturn(List.of(candidate));

		List<CandidatePingDto> list = service.getPendingPingsForProvider(provider1.getId());

		assertThat(list).hasSize(1);
		assertThat(list.getFirst().resourceLabel()).isEqualTo("Motobombas");
		assertThat(list.getFirst().status()).isEqualTo(CandidateStatus.PENDING);
	}
}

