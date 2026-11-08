package eytlyr.com.utp_comercio_backend.calls;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import eytlyr.com.utp_comercio_backend.chat.ChatService;
import eytlyr.com.utp_comercio_backend.common.*;
import eytlyr.com.utp_comercio_backend.notification.NotificationService;

/** Signaling only. Audio is encrypted WebRTC traffic between the devices. */
@Service
@Transactional(readOnly = true)
public class VoiceCallService {
    public record Call(UUID id, UUID conversationId, UUID callerId, UUID calleeId,
                       String callerName, String calleeName, String state,
                       String offer, String answer, Instant expiresAt) { }
    private final Map<UUID, Call> calls = new HashMap<>();
    private final ChatService chat;
    private final Clock clock;
    private final String stun, turn, secret;

    @Autowired(required = false)
    private NotificationService notifications;

    public VoiceCallService(ChatService chat, Clock clock,
        @Value("${NEARBY_STUN_URL:stun:stun.l.google.com:19302}") String stun,
        @Value("${NEARBY_TURN_URL:}") String turn,
        @Value("${NEARBY_TURN_SECRET:}") String secret) {
        this.chat = chat; this.clock = clock; this.stun = stun; this.turn = turn; this.secret = secret;
    }
    private void cleanup() {
        var now = clock.instant();
        calls.replaceAll((id, c) -> c.expiresAt().isBefore(now) && !terminal(c)
            ? changed(c, "MISSED", c.answer(), now.plusSeconds(120)) : c);
        calls.values().removeIf(c -> terminal(c) && c.expiresAt().isBefore(now));
    }
    private boolean terminal(Call c) { return Set.of("ENDED", "REJECTED", "MISSED").contains(c.state()); }
    private Call changed(Call c, String state, String answer, Instant expires) {
        return new Call(c.id(), c.conversationId(), c.callerId(), c.calleeId(), c.callerName(), c.calleeName(), state, c.offer(), answer, expires);
    }
    private Call authorized(UUID me, UUID id) {
        cleanup();
        var c = calls.get(id);
        if (c == null) throw NotFoundException.of("la llamada");
        if (!c.callerId().equals(me) && !c.calleeId().equals(me))
            throw new ForbiddenException("CALL_FORBIDDEN", "Esta llamada no es tuya.");
        return c;
    }
    public synchronized Call start(UUID me, UUID conversationId, String offer) {
        cleanup();
        var conversation = chat.participantOf(me, conversationId);
        var other = conversation.other(me);
        if (calls.values().stream().anyMatch(c -> !terminal(c) &&
            (Set.of(c.callerId(), c.calleeId()).contains(me) || Set.of(c.callerId(), c.calleeId()).contains(other.getId()))))
            throw new BusinessRuleException("CALL_BUSY", "Uno de los usuarios ya tiene una llamada activa.");
        if (calls.size() >= 1000) throw new BusinessRuleException("CALL_LIMIT", "No se pudo iniciar la llamada. Inténtalo después.");
        var caller = conversation.getUserA().getId().equals(me) ? conversation.getUserA() : conversation.getUserB();
        var c = new Call(UUID.randomUUID(), conversationId, me, other.getId(), caller.getName(), other.getName(), "RINGING", offer, null, clock.instant().plusSeconds(90));
        calls.put(c.id(), c);
        if (notifications != null) {
            notifications.notifyUser(
                other.getId(),
                "Llamada entrante de Nearby",
                caller.getName() + " te está llamando...",
                Map.of(
                    "type", "VOICE_CALL",
                    "callId", c.id().toString(),
                    "conversationId", conversationId.toString(),
                    "callerName", caller.getName()
                )
            );
        }
        return c;
    }
    public synchronized List<Call> incoming(UUID me) {
        cleanup(); return calls.values().stream().filter(c -> c.calleeId().equals(me) && c.state().equals("RINGING")).toList();
    }
    public synchronized Call get(UUID me, UUID id) {
        var c = authorized(me, id);
        if (c.state().equals("ANSWERED")) {
            c = changed(c, c.state(), c.answer(), clock.instant().plusSeconds(45));
            calls.put(id, c);
        }
        return c;
    }
    public synchronized Call answer(UUID me, UUID id, String answer) {
        var c = authorized(me, id);
        if (!c.calleeId().equals(me)) throw new ForbiddenException("CALL_FORBIDDEN", "Solo el destinatario puede aceptar.");
        if (!c.state().equals("RINGING")) throw new BusinessRuleException("CALL_CLOSED", "La llamada ya no está disponible.");
        c = changed(c, "ANSWERED", answer, clock.instant().plusSeconds(45)); calls.put(id, c); return c;
    }
    public synchronized void end(UUID me, UUID id) {
        var c = authorized(me, id);
        calls.put(id, changed(c, c.state().equals("RINGING") && me.equals(c.calleeId()) ? "REJECTED" : "ENDED", c.answer(), clock.instant().plusSeconds(120)));
    }
    public Map<String, Object> configuration(UUID me) {
        var servers = new ArrayList<Map<String, String>>();
        servers.add(Map.of("urls", stun));
        if (!turn.isBlank() && !secret.isBlank()) {
            try {
                var username = (clock.instant().getEpochSecond() + 10800) + ":" + me;
                var mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
                servers.add(Map.of("urls", turn, "username", username, "credential", Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)))));
            } catch (Exception e) { throw new IllegalStateException("No se pudo configurar TURN", e); }
        }
        return Map.of("iceServers", servers);
    }
}
