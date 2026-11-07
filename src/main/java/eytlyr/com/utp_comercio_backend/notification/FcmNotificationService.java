package eytlyr.com.utp_comercio_backend.notification;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.user.AppUser;

/**
 * Push real con Firebase Cloud Messaging (nearby.notifications.provider=fcm). Preparado, sin implementar.
 * Para activarlo: agregar firebase-admin, leer GOOGLE_APPLICATION_CREDENTIALS y enviar a los tokens
 * de device_token del usuario o de la zona.
 */
@Component
@ConditionalOnProperty(prefix = "nearby.notifications", name = "provider", havingValue = "fcm")
public class FcmNotificationService implements NotificationService {

	@Override
	public void notifyUser(UUID userId, String title, String body, Map<String, String> data) {
		throw new UnsupportedOperationException("El envío por FCM aún no está implementado.");
	}

	@Override
	public int notifyZone(UUID zoneId, String title, String body, Map<String, String> data, Predicate<AppUser> filter) {
		throw new UnsupportedOperationException("El envío por FCM aún no está implementado.");
	}
}
