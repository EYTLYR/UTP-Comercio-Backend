package eytlyr.com.utp_comercio_backend.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import eytlyr.com.utp_comercio_backend.user.AppUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Si el microservicio SMS no responde, el resto de avisos del SOS se envía igual y no se lanza error. */
class DispatchListenerTest {

	@Test
	void failedSmsDoesNotStopTheOtherAlerts() {
		List<String> sent = new ArrayList<>();
		NotificationService push = new NotificationService() {
			@Override
			public void notifyUser(UUID userId, String title, String body, Map<String, String> data) {
				sent.add("push:" + title);
			}

			@Override
			public int notifyZone(UUID zoneId, String title, String body, Map<String, String> data,
					Predicate<AppUser> filter) {
				return 0;
			}
		};
		DispatchListener listener = new DispatchListener(push, (phone, text) -> {
			if (phone.equals("911111111")) {
				throw new IllegalStateException("El microservicio SMS no respondió");
			}
			sent.add("sms:" + phone);
		});

		Dispatch d = Dispatch.builder()
				.push(UUID.randomUUID(), "SOS: María necesita ayuda", "A 120 m de ti", Map.of())
				.sms("911111111", "NEARBY SOS ...")
				.sms("922222222", "NEARBY SOS ...")
				.build();

		assertThatCode(() -> listener.onDispatch(d)).doesNotThrowAnyException();
		assertThat(sent).containsExactly("push:SOS: María necesita ayuda", "sms:922222222");
	}
}
