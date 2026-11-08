package eytlyr.com.utp_comercio_backend.posts;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import eytlyr.com.utp_comercio_backend.services.dto.ServiceDtos.ConversationRef;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

/** Muro de la cadena de favores (Módulo 2): sin dinero, idempotente por clientId para funcionar sin señal. */
@Service
public class PostService {

	public static final String ZONE_REQUIRED = "ZONE_REQUIRED";

	public static final String POST_NOT_AUTHOR = "POST_NOT_AUTHOR";

	public static final String POST_SELF = "POST_SELF";

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
				return new Created<>(toDto(existing.get(), me, Set.of()), false);
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
		return new Created<>(toDto(p, me, Set.of()), true);
	}

	@Transactional(readOnly = true)
	public PageResponse<PostDto> list(UUID userId, PostType type, PostCategory category, int radiusM, int page,
			int size) {
		AppUser me = user(userId);
		Set<UUID> responded = responses.postIdsRespondedBy(userId);
		List<Post> source;
		if (me.getLat() != null && me.getLng() != null) {
			// Vecinos cercanos aunque estén en la zona de al lado.
			double[] b = GeoUtils.box(me.getLat(), me.getLng(), radiusM);
			source = posts.findNear(b[0], b[1], b[2], b[3], VISIBLE);
		}
		else if (me.getZone() != null) {
			source = posts.findInZone(me.getZone().getId(), VISIBLE);
		}
		else {
			return new PageResponse<>(List.of(), page, size, 0, 0);
		}
		List<PostDto> all = source.stream()
				.filter(p -> type == null || p.getType() == type)
				.filter(p -> category == null || p.getCategory() == category)
				.map(p -> withResponses(toDto(p, me, responded)))
				.filter(p -> p.distanceM() == null || p.distanceM() <= radiusM)
				.toList();
		int s = Math.max(1, Math.min(size, 50));
		int pg = Math.max(0, page);
		int from = Math.min(pg * s, all.size());
		return new PageResponse<>(all.subList(from, Math.min(from + s, all.size())), pg, s, all.size(),
				(all.size() + s - 1) / s);
	}

	@Transactional(readOnly = true)
	public PostDto get(UUID userId, UUID postId) {
		return toDto(post(postId), user(userId), responses.postIdsRespondedBy(userId));
	}

	@Transactional
	public PostDto updateStatus(UUID userId, UUID postId, PostStatus status) {
		Post p = authored(userId, postId);
		p.setStatus(status);
		return toDto(p, p.getAuthor(), Set.of());
	}

	/** Borrado lógico: la publicación queda CANCELLED. */
	@Transactional
	public void cancel(UUID userId, UUID postId) {
		authored(userId, postId).setStatus(PostStatus.CANCELLED);
	}

	/** "Yo te ayudo" / "Me interesa": abre el chat con el autor con un mensaje del sistema. */
	@Transactional
	public ConversationRef respond(UUID userId, UUID postId) {
		Post p = post(postId);
		AppUser me = user(userId);
		AppUser author = p.getAuthor();
		if (author.getId().equals(userId)) {
			throw new BusinessRuleException(POST_SELF, "No puedes responder a tu propia publicación.");
		}
		Conversation c = chat.getOrCreate(me, author, p, null, "Sobre: " + p.getText());
		if (!responses.existsByPostIdAndResponderId(postId, userId)) {
			responses.save(PostResponse.builder().post(p).responder(me).build());
			chat.systemMessage(c, (p.getType() == PostType.NEED ? "Le ofreciste ayuda: «" : "Te interesa: «")
					+ p.getText() + "»");
			notifications.notifyUser(author.getId(),
					p.getType() == PostType.NEED ? me.getName() + " quiere ayudarte" : me.getName() + " está interesado",
					p.getText(), Map.of("type", "post_response", "conversationId", c.getId().toString()));
		}
		return new ConversationRef(c.getId());
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

	/** En mis publicaciones, cuántos vecinos respondieron. */
	private PostDto withResponses(PostDto d) {
		if (!d.mine()) {
			return d;
		}
		return new PostDto(d.id(), d.type(), d.category(), d.categoryLabel(), d.text(), d.authorName(),
				d.authorInitials(), d.distanceM(), d.createdAt(), d.respondedByMe(), d.status(), true,
				responses.countByPostId(d.id()), d.authorZoneName());
	}

	private static PostDto toDto(Post p, AppUser viewer, Set<UUID> responded) {
		Integer distance = viewer.getLat() != null && viewer.getLng() != null
				? (int) Math.round(GeoUtils.haversineMeters(viewer.getLat(), viewer.getLng(), p.getLat(), p.getLng()))
				: null;
		AppUser a = p.getAuthor();
		return new PostDto(p.getId(), p.getType(), p.getCategory(), p.getCategory().label(), p.getText(), a.getName(),
				TextUtils.initials(a.getName()), distance, p.getCreatedAt(), responded.contains(p.getId()),
				p.getStatus(), a.getId().equals(viewer.getId()), 0, a.getZone() != null ? a.getZone().getName() : null);
	}
}
