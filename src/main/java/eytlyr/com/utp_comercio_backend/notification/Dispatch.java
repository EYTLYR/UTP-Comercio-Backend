package eytlyr.com.utp_comercio_backend.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Avisos que se envían DESPUÉS del commit y en segundo plano (DispatchListener).
 * Así un SOS nunca falla ni se demora por el proveedor de push o de SMS.
 */
public record Dispatch(List<Push> pushes, List<Sms> sms, List<ZonePush> zonePushes) {

	public record Push(UUID userId, String title, String body, Map<String, String> data) {
	}

	public record Sms(String phone, String text) {
	}

	public record ZonePush(UUID zoneId, String title, String body, Map<String, String> data) {
	}

	public static Builder builder() {
		return new Builder();
	}

	public boolean isEmpty() {
		return pushes.isEmpty() && sms.isEmpty() && zonePushes.isEmpty();
	}

	public static final class Builder {

		private final List<Push> pushes = new ArrayList<>();

		private final List<Sms> sms = new ArrayList<>();

		private final List<ZonePush> zonePushes = new ArrayList<>();

		public Builder push(UUID userId, String title, String body, Map<String, String> data) {
			pushes.add(new Push(userId, title, body, data));
			return this;
		}

		public Builder sms(String phone, String text) {
			sms.add(new Sms(phone, text));
			return this;
		}

		public Builder zone(UUID zoneId, String title, String body, Map<String, String> data) {
			zonePushes.add(new ZonePush(zoneId, title, body, data));
			return this;
		}

		public Dispatch build() {
			return new Dispatch(List.copyOf(pushes), List.copyOf(sms), List.copyOf(zonePushes));
		}
	}
}
