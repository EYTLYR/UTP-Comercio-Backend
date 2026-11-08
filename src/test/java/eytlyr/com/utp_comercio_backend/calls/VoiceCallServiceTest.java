package eytlyr.com.utp_comercio_backend.calls;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import eytlyr.com.utp_comercio_backend.chat.*;
import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.common.*;

class VoiceCallServiceTest {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), outsider = UUID.randomUUID(), conversation = UUID.randomUUID();
    ChatService chat = mock(ChatService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC);
    VoiceCallService service;
    @BeforeEach void setup() {
        var ua = new AppUser(); ua.setId(a); ua.setName("Ana");
        var ub = new AppUser(); ub.setId(b); ub.setName("Luis");
        var c = Conversation.builder().userA(ua).userB(ub).build(); c.setId(conversation);
        when(chat.participantOf(a, conversation)).thenReturn(c);
        service = new VoiceCallService(chat, clock, "stun:stun.example:3478", "", "");
    }
    @Test void onlyCalleeReceivesAndAnswersAndEitherCanHangUp() {
        var call = service.start(a, conversation, "offer");
        assertThat(service.incoming(a)).isEmpty();
        assertThat(service.incoming(b)).containsExactly(call);
        assertThatThrownBy(() -> service.answer(a, call.id(), "bad")).isInstanceOf(ForbiddenException.class);
        assertThat(service.answer(b, call.id(), "answer").state()).isEqualTo("ANSWERED");
        assertThat(service.get(a, call.id()).answer()).isEqualTo("answer");
        service.end(b, call.id());
        assertThat(service.get(a, call.id()).state()).isEqualTo("ENDED");
    }
    @Test void outsiderCannotReadSdpOrEndTheCall() {
        var call = service.start(a, conversation, "private-offer");
        assertThatThrownBy(() -> service.get(outsider, call.id())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.end(outsider, call.id())).isInstanceOf(ForbiddenException.class);
    }
    @Test void concurrentCallIsRejectedUntilPreviousCallEnds() {
        var call = service.start(a, conversation, "offer");
        assertThatThrownBy(() -> service.start(a, conversation, "duplicate")).isInstanceOf(BusinessRuleException.class);
        service.end(a, call.id());
        assertThat(service.start(a, conversation, "new-offer").id()).isNotEqualTo(call.id());
    }
    @Test void nonParticipantCannotStartSignaling() {
        when(chat.participantOf(outsider, conversation)).thenThrow(new ForbiddenException("CHAT_FORBIDDEN", "Forbidden"));
        assertThatThrownBy(() -> service.start(outsider, conversation, "offer")).isInstanceOf(ForbiddenException.class);
    }
    @Test void turnCredentialsExpireAndDoNotExposeSharedSecret() {
        var configured = new VoiceCallService(chat, clock, "stun:stun.example:3478", "turn:relay.example:3478", "test-shared-secret");
        var settings = configured.configuration(a).toString();
        assertThat(settings).contains("turn:relay.example:3478", a.toString()).doesNotContain("test-shared-secret");
    }
    @Test void recipientCanRejectBeforeOpeningMicrophone() {
        var call = service.start(a, conversation, "offer");
        service.end(b, call.id());
        assertThat(service.get(a, call.id()).state()).isEqualTo("REJECTED");
        assertThat(service.incoming(b)).isEmpty();
        assertThatThrownBy(() -> service.answer(b, call.id(), "late")).isInstanceOf(BusinessRuleException.class);
    }
    @Test void unansweredCallsExpireAndReleaseParticipants() {
        var now = new java.util.concurrent.atomic.AtomicReference<>(clock.instant());
        var ticking = mock(Clock.class);
        when(ticking.instant()).thenAnswer(ignored -> now.get());
        var expiring = new VoiceCallService(chat, ticking, "stun:example:3478", "", "");
        var call = expiring.start(a, conversation, "offer");
        now.set(now.get().plusSeconds(91));
        assertThat(expiring.incoming(b)).isEmpty();
        assertThat(expiring.get(a, call.id()).state()).isEqualTo("MISSED");
        assertThat(expiring.start(a, conversation, "retry").id()).isNotEqualTo(call.id());
    }

}
