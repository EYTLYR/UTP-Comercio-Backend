package eytlyr.com.utp_comercio_backend.posts;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.ai.ContentCreatedEvent;
import eytlyr.com.utp_comercio_backend.ai.SourceType;
import eytlyr.com.utp_comercio_backend.chat.ChatService;
import eytlyr.com.utp_comercio_backend.chat.Conversation;
import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.common.Created;
import eytlyr.com.utp_comercio_backend.common.ForbiddenException;
import eytlyr.com.utp_comercio_backend.common.GeoUtils;
import eytlyr.com.utp_comercio_backend.common.NotFoundException;
import eytlyr.com.utp_comercio_backend.common.PageResponse;
import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.notification.NotificationService;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.NeighborhoodStats;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.PostDto;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.PostRequest;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.PostResponseDto;
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ConversationRef;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

/** Muro de la cadena de favores (Módulo 2): sin dinero, idempotente por clientId para funcionar sin señal. */
@Service
public class PostService {

	public static final String ZONE_REQUIRED = "ZONE_REQUIRED";

	public static final String POST_NOT_AUTHOR = "POST_NOT_AUTHOR";

	public static final String POST_SELF = "POST_SELF";

	public static final String TOO_MANY_ACTIVE_RESPONSES = "TOO_MANY_ACTIVE_RESPONSES";

	public static final String RESPONSE_NOT_PENDING = "RESPONSE_NOT_PENDING";

	static final int MONTHLY_GOAL = 180;

	private static final Set<PostStatus> VISIBLE = EnumSet.of(PostStatus.OPEN, PostStatus.MATCHED);

	private final PostRepository posts;

	private final PostResponseRepository responses;

	private final AppUserRepository users;

	private final ChatService chat;

	private final NotificationService notifications;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public PostService(PostRepository posts, PostResponseRepository responses, AppUserRepository users, ChatService chat,
			NotificationService notifications, ApplicationEventPublisher events, Clock clock) {
		this.posts = posts;
		this.responses = responses;
		this.users = users;
		this.chat = chat;
		this.notifications = notifications;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public Created<PostDto> create(UUID userId, PostRequest req) {
		AppUser me = user(userId);
		if (req.clientId() != null) {
			var existing = posts.findByAuthorIdAndClientId(userId, req.clientId());
			if (existing.isPresent()) {
				return new Created<>(withResponses(toDto(existing.get(), me, Map.of())), false);
			}
		}
		if (me.getZone() == null) {
			throw new BusinessRuleException(ZONE_REQUIRED, "Primero guarda tu ubicación para publicar en tu barrio.");
		}
		Double lat = req.lat() != null ? req.lat() : me.getLat();
		Double lng = req.lng() != null ? req.lng() : me.getLng();
		String text = req.text() == null || req.text().isBlank() ? defaultText(req.type(), req.category())
				: req.text().trim();
		Post p = posts.save(Post.builder()
				.author(me)
				.zone(me.getZone())
				.type(req.type())
				.category(req.category())
				.text(text)
				.photoUrl(req.photoUrl())
				.lat(lat != null ? lat : me.getZone().getCenterLat())
				.lng(lng != null ? lng : me.getZone().getCenterLng())
				.clientId(req.clientId())
				.build());
		events.publishEvent(new ContentCreatedEvent(SourceType.POST, p.getId()));
		return new Created<>(withResponses(toDto(p, me, Map.of())), true);
	}

	@Transactional(readOnly = true)
	public PageResponse<PostDto> list(UUID userId, PostType type, PostCategory category, int radiusM, int page,
			int size) {
		AppUser me = user(userId);
		Map<UUID, PostResponseStatus> responded = responses.findByResponderId(userId).stream()
				.collect(Collectors.toMap(r -> r.getPost().getId(), PostResponse::getStatus, (a, b) -> a));
		int effectiveRadius = Math.max(radiusM, me.getZone() != null ? Math.max(me.getZone().getRadiusM(), 5000) : 5000);
		Set<Post> sourceSet = new LinkedHashSet<>();
		if (me.getZone() != null) {
			sourceSet.addAll(posts.findInZone(me.getZone().getId(), VISIBLE));
		}
		Double refLat = me.getLat() != null ? me.getLat() : (me.getZone() != null ? me.getZone().getCenterLat() : null);
		Double refLng = me.getLng() != null ? me.getLng() : (me.getZone() != null ? me.getZone().getCenterLng() : null);
		if (refLat != null && refLng != null) {
			double[] b = GeoUtils.box(refLat, refLng, effectiveRadius);
			sourceSet.addAll(posts.findNear(b[0], b[1], b[2], b[3], VISIBLE));
		}
		else if (sourceSet.isEmpty()) {
			return new PageResponse<>(List.of(), page, size, 0, 0);
		}
		List<PostDto> all = sourceSet.stream()
				.filter(p -> type == null || p.getType() == type)
				.filter(p -> category == null || p.getCategory() == category)
				.map(p -> withResponses(toDto(p, me, responded)))
				.filter(p -> p.distanceM() == null || p.distanceM() <= effectiveRadius
						|| (me.getZone() != null && p.authorZoneName() != null && me.getZone().getName().equals(p.authorZoneName())))
				.sorted(Comparator.comparing(PostDto::createdAt).reversed())
				.toList();
		int s = Math.max(1, Math.min(size, 50));
		int pg = Math.max(0, page);
		int from = Math.min(pg * s, all.size());
		return new PageResponse<>(all.subList(from, Math.min(from + s, all.size())), pg, s, all.size(),
				(all.size() + s - 1) / s);
	}

	@Transactional(readOnly = true)
	public PostDto get(UUID userId, UUID postId) {
		Map<UUID, PostResponseStatus> responded = responses.findByResponderId(userId).stream()
				.collect(Collectors.toMap(r -> r.getPost().getId(), PostResponse::getStatus, (a, b) -> a));
		return withResponses(toDto(post(postId), user(userId), responded));
	}

	@Transactional
	public PostDto updateStatus(UUID userId, UUID postId, PostStatus status) {
		Post p = authored(userId, postId);
		p.setStatus(status);
		return withResponses(toDto(p, p.getAuthor(), Map.of()));
	}

	/** Borrado lógico: la publicación queda CANCELLED. */
	@Transactional
	public void cancel(UUID userId, UUID postId) {
		authored(userId, postId).setStatus(PostStatus.CANCELLED);
	}

	/** "Yo te ayudo" / "Me interesa": crea una solicitud de respuesta en estado PENDING sin abrir el chat. */
	@Transactional
	public PostResponseDto respond(UUID userId, UUID postId) {
		Post p = post(postId);
		AppUser me = user(userId);
		AppUser author = p.getAuthor();
		if (author.getId().equals(userId)) {
			throw new BusinessRuleException(POST_SELF, "No puedes responder a tu propia publicación.");
		}
		long activeCount = responses.countByResponderIdAndStatusIn(userId,
				List.of(PostResponseStatus.PENDING, PostResponseStatus.IN_PROGRESS));
		if (activeCount >= 5) {
			throw new BusinessRuleException(TOO_MANY_ACTIVE_RESPONSES,
					"Ya tienes 5 solicitudes de ayuda activas. Espera a que se resuelvan antes de ofrecerte en más.");
		}
		PostResponse r = responses.findByPostIdAndResponderId(postId, userId)
				.orElseGet(() -> responses.save(PostResponse.builder()
						.post(p)
						.responder(me)
						.status(PostResponseStatus.PENDING)
						.build()));
		if (r.getStatus() == PostResponseStatus.REJECTED) {
			r.setStatus(PostResponseStatus.PENDING);
			r = responses.save(r);
		}
		notifications.notifyUser(author.getId(),
				p.getType() == PostType.NEED ? me.getName() + " quiere ayudarte" : me.getName() + " está interesado",
				p.getText(), Map.of("type", "post_response", "postId", postId.toString(), "responseId", r.getId().toString()));
		return toResponseDto(r, null);
	}

	/** Autor acepta la solicitud de un vecino: pasa a IN_PROGRESS y abre el canal de chat bidireccional. */
	@Transactional
	public ConversationRef acceptResponse(UUID userId, UUID postId, UUID responseId) {
		Post p = post(postId);
		if (!p.getAuthor().getId().equals(userId)) {
			throw new ForbiddenException(POST_NOT_AUTHOR, "Solo el autor puede aceptar solicitudes.");
		}
		PostResponse r = responses.findById(responseId)
				.orElseThrow(() -> NotFoundException.of("la solicitud"));
		if (!r.getPost().getId().equals(postId)) {
			throw NotFoundException.of("la solicitud");
		}
		if (r.getStatus() != PostResponseStatus.PENDING) {
			throw new BusinessRuleException(RESPONSE_NOT_PENDING, "Esta solicitud ya no está pendiente.");
		}
		r.setStatus(PostResponseStatus.IN_PROGRESS);
		responses.save(r);

		p.setStatus(PostStatus.MATCHED);
		posts.save(p);

		AppUser author = p.getAuthor();
		AppUser responder = r.getResponder();
		Conversation c = chat.getOrCreate(author, responder, p, null, "Sobre: " + p.getText());
		chat.systemMessage(c, (p.getType() == PostType.NEED ? "Aceptaste la ayuda de " : "Aceptaste el interés de ")
				+ responder.getName() + " para: «" + p.getText() + "»");
		notifications.notifyUser(responder.getId(),
				author.getName() + " aceptó tu ayuda",
				p.getText(),
				Map.of("type", "response_accepted", "conversationId", c.getId().toString(), "postId", postId.toString()));
		return new ConversationRef(c.getId());
	}

	/** Autor rechaza la solicitud de un vecino: pasa a REJECTED. */
	@Transactional
	public PostResponseDto rejectResponse(UUID userId, UUID postId, UUID responseId) {
		Post p = post(postId);
		if (!p.getAuthor().getId().equals(userId)) {
			throw new ForbiddenException(POST_NOT_AUTHOR, "Solo el autor puede rechazar solicitudes.");
		}
		PostResponse r = responses.findById(responseId)
				.orElseThrow(() -> NotFoundException.of("la solicitud"));
		if (!r.getPost().getId().equals(postId)) {
			throw NotFoundException.of("la solicitud");
		}
		if (r.getStatus() != PostResponseStatus.PENDING) {
			throw new BusinessRuleException(RESPONSE_NOT_PENDING, "Esta solicitud ya no está pendiente.");
		}
		r.setStatus(PostResponseStatus.REJECTED);
		responses.save(r);
		notifications.notifyUser(r.getResponder().getId(),
				"Solicitud no aceptada",
				"Tu ofrecimiento para el favor de " + p.getAuthor().getName() + " no fue aceptado.",
				Map.of("type", "response_rejected", "postId", postId.toString()));
		return toResponseDto(r, null);
	}

	/** Lista de solicitudes recibidas para una publicación (solo el autor). */
	@Transactional(readOnly = true)
	public List<PostResponseDto> listResponses(UUID userId, UUID postId) {
		Post p = post(postId);
		if (!p.getAuthor().getId().equals(userId)) {
			throw new ForbiddenException(POST_NOT_AUTHOR, "Solo el autor puede ver las solicitudes.");
		}
		return responses.findByPostIdOrderByCreatedAtDesc(postId).stream()
				.map(r -> toResponseDto(r, null))
				.toList();
	}

	/** Favores de la semana en la zona (respuestas a publicaciones de los últimos 7 días). */
	@Transactional(readOnly = true)
	public NeighborhoodStats stats(UUID userId) {
		AppUser me = user(userId);
		long week = me.getZone() == null ? 0
				: responses.countByPostZoneIdAndCreatedAtAfter(me.getZone().getId(), OffsetDateTime.now(clock).minusDays(7));
		return new NeighborhoodStats(week, MONTHLY_GOAL, responses.countByResponderId(userId));
	}

	// ---------------------------------------------------------------- utilidades

	static String defaultText(PostType type, PostCategory category) {
		String label = category.label();
		return (type == PostType.NEED ? "Necesito: " : "Ofrezco: ")
				+ Character.toLowerCase(label.charAt(0)) + label.substring(1);
	}

	private Post post(UUID postId) {
		return posts.findById(postId).orElseThrow(() -> NotFoundException.of("la publicación"));
	}

	private Post authored(UUID userId, UUID postId) {
		Post p = post(postId);
		if (!p.getAuthor().getId().equals(userId)) {
			throw new ForbiddenException(POST_NOT_AUTHOR, "Solo el autor puede cambiar esta publicación.");
		}
		return p;
	}

	private AppUser user(UUID userId) {
		return users.findById(userId).orElseThrow(() -> NotFoundException.of("el usuario"));
	}

	private static PostResponseDto toResponseDto(PostResponse r, UUID conversationId) {
		AppUser resp = r.getResponder();
		return new PostResponseDto(r.getId(), r.getPost().getId(), resp.getId(), resp.getName(),
				TextUtils.initials(resp.getName()), r.getStatus(), r.getCreatedAt(), conversationId);
	}

	/** En mis publicaciones, cuántos vecinos respondieron y la lista de solicitudes entrantes. */
	private PostDto withResponses(PostDto d) {
		if (!d.mine()) {
			return d;
		}
		List<PostResponseDto> incoming = responses.findByPostIdOrderByCreatedAtDesc(d.id()).stream()
				.map(r -> toResponseDto(r, null))
				.toList();
		return new PostDto(d.id(), d.type(), d.category(), d.categoryLabel(), d.text(), d.authorName(),
				d.authorInitials(), d.distanceM(), d.createdAt(), d.respondedByMe(), d.status(), true,
				incoming.size(), d.authorZoneName(), d.lat(), d.lng(), d.myResponseStatus(), incoming);
	}

	private static PostDto toDto(Post p, AppUser viewer, Map<UUID, PostResponseStatus> myResponseMap) {
		boolean isMine = p.getAuthor().getId().equals(viewer.getId());
		double lat = p.getLat();
		double lng = p.getLng();
		// Si el usuario solicitante no es el autor del post y el post está en estado OPEN,
		// reemplaza las coordenadas reales por las ofuscadas (Geofuzzing 100-300m)
		if (!isMine && p.getStatus() == PostStatus.OPEN) {
			double[] fuzzed = GeoUtils.geofuzz(lat, lng);
			lat = fuzzed[0];
			lng = fuzzed[1];
		}
		Integer distance = viewer.getLat() != null && viewer.getLng() != null
				? (int) Math.round(GeoUtils.haversineMeters(viewer.getLat(), viewer.getLng(), lat, lng))
				: null;
		AppUser a = p.getAuthor();
		PostResponseStatus myStatus = myResponseMap.get(p.getId());
		return new PostDto(p.getId(), p.getType(), p.getCategory(), p.getCategory().label(), p.getText(), a.getName(),
				TextUtils.initials(a.getName()), distance, p.getCreatedAt(), myStatus != null,
				p.getStatus(), isMine, 0, a.getZone() != null ? a.getZone().getName() : null,
				lat, lng, myStatus, List.of());
	}
}
