package eytlyr.com.utp_comercio_backend.posts;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.common.PageResponse;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.NeighborhoodStats;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.PostDto;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.PostRequest;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.PostResponseDto;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.StatusRequest;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ConversationRef;

@RestController
@Tag(name = "Muro de favores", description = "Cadena de favores sin dinero")
public class PostController {

	private final PostService posts;

	private final CurrentUser currentUser;

	public PostController(PostService posts, CurrentUser currentUser) {
		this.posts = posts;
		this.currentUser = currentUser;
	}

	@PostMapping("/api/v1/posts")
	@Operation(summary = "Publicar (idempotente por clientId: 201 nueva, 200 si ya existía)")
	public ResponseEntity<PostDto> create(@Valid @RequestBody PostRequest body) {
		return posts.create(currentUser.id(), body).toResponse();
	}

	@GetMapping("/api/v1/posts")
	@Operation(summary = "Publicaciones abiertas del barrio, la más reciente primero")
	public PageResponse<PostDto> list(@RequestParam(required = false) PostType type,
			@RequestParam(required = false) PostCategory category, @RequestParam(defaultValue = "5000") int radiusM,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.clamp(size, 1, 50);
		int safeRadius = Math.clamp(radiusM, 100, 10000);
		return posts.list(currentUser.id(), type, category, safeRadius, safePage, safeSize);
	}

	@GetMapping("/api/v1/posts/{id}")
	public PostDto get(@PathVariable UUID id) {
		return posts.get(currentUser.id(), id);
	}

	@PatchMapping("/api/v1/posts/{id}/status")
	@Operation(summary = "Cambiar el estado (solo el autor)")
	public PostDto status(@PathVariable UUID id, @Valid @RequestBody StatusRequest body) {
		return posts.updateStatus(currentUser.id(), id, body.status());
	}

	@DeleteMapping("/api/v1/posts/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Quitar mi publicación (queda CANCELLED)")
	public void delete(@PathVariable UUID id) {
		posts.cancel(currentUser.id(), id);
	}

	@PostMapping("/api/v1/posts/{id}/responses")
	@Operation(summary = "Yo te ayudo / Me interesa: crea la solicitud PENDING sin abrir chat")
	public PostResponseDto respond(@PathVariable UUID id) {
		return posts.respond(currentUser.id(), id);
	}

	@GetMapping("/api/v1/posts/{id}/responses")
	@Operation(summary = "Ver las solicitudes de ayuda recibidas para mi publicación")
	public List<PostResponseDto> listResponses(@PathVariable UUID id) {
		return posts.listResponses(currentUser.id(), id);
	}

	@PostMapping("/api/v1/posts/{id}/responses/{responseId}/accept")
	@Operation(summary = "Aceptar solicitud de ayuda: pasa a IN_PROGRESS y abre el chat")
	public ConversationRef acceptResponse(@PathVariable UUID id, @PathVariable UUID responseId) {
		return posts.acceptResponse(currentUser.id(), id, responseId);
	}

	@PostMapping("/api/v1/posts/{id}/responses/{responseId}/reject")
	@Operation(summary = "Rechazar solicitud de ayuda: pasa a REJECTED")
	public PostResponseDto rejectResponse(@PathVariable UUID id, @PathVariable UUID responseId) {
		return posts.rejectResponse(currentUser.id(), id, responseId);
	}

	@GetMapping("/api/v1/neighborhood/stats")
	@Operation(summary = "Favores de la semana en mi barrio")
	public NeighborhoodStats stats() {
		return posts.stats(currentUser.id());
	}
}
