package eytlyr.com.utp_comercio_backend.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import eytlyr.com.utp_comercio_backend.chat.ChatService;
import eytlyr.com.utp_comercio_backend.chat.dto.ChatDtos.SendMessageRequest;
import eytlyr.com.utp_comercio_backend.common.ApiException;
import eytlyr.com.utp_comercio_backend.common.Created;
import eytlyr.com.utp_comercio_backend.emergency.EmergencyService;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.VulnerableReportRequest;
import eytlyr.com.utp_comercio_backend.posts.PostService;
import eytlyr.com.utp_comercio_backend.posts.dto.PostDtos.PostRequest;
import eytlyr.com.utp_comercio_backend.prevention.PreventionService;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskReportRequest;
import eytlyr.com.utp_comercio_backend.sos.SosService;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.CheckInRequest;
import eytlyr.com.utp_comercio_backend.sos.dto.SosDtos.SosRequest;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.OutboxItem;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.OutboxResult;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.OutboxStatus;

/**
 * Cola del teléfono al volver la señal. Cada elemento se procesa en orden y en su propia transacción
 * (la de cada servicio): uno con error no impide los demás. Repetir un clientId devuelve DUPLICATE.
 */
@Service
public class OutboxProcessor {

	private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

	static final int MAX_ITEMS = 100;

	private final PostService posts;

	private final SosService sos;

	private final EmergencyService emergency;

	private final PreventionService prevention;

	private final ChatService chat;

	private final JsonMapper json;

	private final Validator validator;

	public OutboxProcessor(PostService posts, SosService sos, EmergencyService emergency, PreventionService prevention,
			ChatService chat, JsonMapper json, Validator validator) {
		this.posts = posts;
		this.sos = sos;
		this.emergency = emergency;
		this.prevention = prevention;
		this.chat = chat;
		this.json = json;
		this.validator = validator;
	}

	public List<OutboxResult> process(UUID userId, List<OutboxItem> items) {
		List<OutboxResult> out = new ArrayList<>();
		for (OutboxItem item : items.stream().limit(MAX_ITEMS).toList()) {
			UUID clientId = clientId(item.payload());
			try {
				out.add(switch (item.kind()) {
					case POST -> {
						PostRequest req = read(item, PostRequest.class);
						if (req.expiresAt() != null && req.expiresAt() < System.currentTimeMillis()) {
							yield new OutboxResult(clientId, item.kind(), OutboxStatus.ERROR, null, "EXPIRED",
									"La publicación ha caducado por superar el tiempo límite sin conexión.");
						}
						yield result(item, clientId, posts.create(userId, req), c -> c.body().id());
					}
					case SOS -> result(item, clientId, sos.create(userId, read(item, SosRequest.class)), c -> c.body().id());
					case VULNERABLE_REPORT -> result(item, clientId,
							emergency.createReport(userId, read(item, VulnerableReportRequest.class)), c -> c.body().id());
					case RISK_REPORT -> result(item, clientId,
							prevention.createReport(userId, read(item, RiskReportRequest.class), null),
							c -> c.body().report().id());
					case MESSAGE -> {
						UUID conversationId = UUID.fromString(String.valueOf(item.payload().get("conversationId")));
						SendMessageRequest msg = read(item, SendMessageRequest.class);
						yield result(item, clientId, chat.send(userId, conversationId, msg.clientId(), msg.text()),
								c -> c.body().id());
					}
					case CHECK_IN -> {
						var dto = sos.checkIn(userId, read(item, CheckInRequest.class).status());
						yield new OutboxResult(clientId, item.kind(), OutboxStatus.CREATED, dto.id(), null, null);
					}
				});
			}
			catch (ApiException ex) {
				out.add(new OutboxResult(clientId, item.kind(), OutboxStatus.ERROR, null, ex.getCode(), ex.getMessage()));
			}
			catch (RuntimeException ex) {
				log.warn("Elemento de la cola sin procesar ({}): {}", item.kind(), ex.getClass().getSimpleName());
				out.add(new OutboxResult(clientId, item.kind(), OutboxStatus.ERROR, null, "INVALID_ITEM",
						"No se pudo procesar este elemento."));
			}
		}
		return out;
	}

	private <T> T read(OutboxItem item, Class<T> type) {
		T value = json.convertValue(item.payload(), type);
		Set<ConstraintViolation<T>> violations = validator.validate(value);
		if (!violations.isEmpty()) {
			throw new ValidationFailed(violations.iterator().next().getMessage());
		}
		return value;
	}

	private static <B> OutboxResult result(OutboxItem item, UUID clientId, Created<B> created,
			java.util.function.Function<Created<B>, UUID> id) {
		return new OutboxResult(clientId, item.kind(), created.created() ? OutboxStatus.CREATED : OutboxStatus.DUPLICATE,
				id.apply(created), null, null);
	}

	private static UUID clientId(Map<String, Object> payload) {
		Object v = payload == null ? null : payload.get("clientId");
		try {
			return v == null ? null : UUID.fromString(v.toString());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	static final class ValidationFailed extends ApiException {

		ValidationFailed(String message) {
			super("VALIDATION_ERROR", message);
		}

		@Override
		public org.springframework.http.HttpStatus getStatus() {
			return org.springframework.http.HttpStatus.BAD_REQUEST;
		}
	}
}
