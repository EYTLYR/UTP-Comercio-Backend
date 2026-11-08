package eytlyr.com.utp_comercio_backend.services;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.common.PageResponse;
import eytlyr.com.utp_comercio_backend.services.ServiceCatalogService.SearchQuery;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ConversationRef;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.EndorsementResult;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ReviewDto;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ReviewRequest;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ServiceDetailDto;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ServiceRequest;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ServiceSummaryDto;

@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "Servicios", description = "Oficios del barrio con Visto Bueno Vecinal")
public class ServiceController {

	private final ServiceCatalogService catalog;

	private final EndorsementService endorsements;

	private final CurrentUser currentUser;

	public ServiceController(ServiceCatalogService catalog, EndorsementService endorsements, CurrentUser currentUser) {
		this.catalog = catalog;
		this.endorsements = endorsements;
		this.currentUser = currentUser;
	}

	@GetMapping
	@Operation(summary = "Buscar oficios cercanos (q sin tildes; sort=endorsements|distance)")
	public PageResponse<ServiceSummaryDto> search(@RequestParam(required = false) ServiceCategory category,
			@RequestParam(required = false) String q, @RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng, @RequestParam(defaultValue = "5000") int radiusM,
			@RequestParam(defaultValue = "false") boolean emergencyOnly,
			@RequestParam(defaultValue = "endorsements") String sort, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.clamp(size, 1, 50);
		int safeRadius = Math.clamp(radiusM, 100, 10000);
		return catalog.search(currentUser.id(),
				new SearchQuery(category, q, lat, lng, safeRadius, emergencyOnly, sort, safePage, safeSize));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalle del oficio")
	public ServiceDetailDto detail(@PathVariable UUID id) {
		return catalog.detail(currentUser.id(), id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Ofrecer mi oficio")
	public ServiceDetailDto create(@Valid @RequestBody ServiceRequest body) {
		return catalog.create(currentUser.id(), body);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Editar mi oficio (solo el dueño)")
	public ServiceDetailDto update(@PathVariable UUID id, @Valid @RequestBody ServiceRequest body) {
		return catalog.update(currentUser.id(), id, body);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Quitar mi oficio (solo el dueño)")
	public void delete(@PathVariable UUID id) {
		catalog.delete(currentUser.id(), id);
	}

	@PostMapping("/{id}/endorsements")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Dar Visto Bueno Vecinal")
	public EndorsementResult endorse(@PathVariable UUID id) {
		return retryOnce(() -> endorsements.endorse(currentUser.id(), id));
	}

	@DeleteMapping("/{id}/endorsements/me")
	@Operation(summary = "Quitar mi Visto Bueno")
	public EndorsementResult withdraw(@PathVariable UUID id) {
		return retryOnce(() -> endorsements.withdraw(currentUser.id(), id));
	}

	@GetMapping("/{id}/reviews")
	@Operation(summary = "Reseñas del oficio")
	public List<ReviewDto> reviews(@PathVariable UUID id) {
		return catalog.reviews(id);
	}

	@PostMapping("/{id}/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Escribir una reseña (5 a 500 caracteres)")
	public ReviewDto addReview(@PathVariable UUID id, @Valid @RequestBody ReviewRequest body) {
		return catalog.addReview(currentUser.id(), id, body);
	}

	@PostMapping("/{id}/contact")
	@Operation(summary = "Botón Mensaje: abre el chat con el dueño")
	public ConversationRef contact(@PathVariable UUID id) {
		return catalog.contact(currentUser.id(), id);
	}

	/** Dos vistos buenos al mismo tiempo chocan por @Version: se reintenta una vez. */
	private static <T> T retryOnce(Supplier<T> action) {
		try {
			return action.get();
		}
		catch (ObjectOptimisticLockingFailureException ex) {
			return action.get();
		}
	}
}
