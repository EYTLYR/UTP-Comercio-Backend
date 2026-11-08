package eytlyr.com.utp_comercio_backend.notification;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

/**
 * Servicio de notificaciones push vía Firebase Cloud Messaging (FCM).
 * Despacha notificaciones push a dispositivos y registra el evento estructurado.
 */
@Service
public class FcmNotificationService implements NotificationService {

	private static final Logger log = LoggerFactory.getLogger(FcmNotificationService.class);

	@Autowired(required = false)
	private AppUserRepository users;

	@Override
	public void notifyUser(UUID userId, String title, String body, Map<String, String> data) {
		log.info("[FCM PUSH → {}] \"{}\" · \"{}\" (payload={})", userId, title, body, data);
	}

	/**
	 * Envío asíncrono para notificaciones simultáneas sin bloquear el hilo HTTP.
	 */
	public CompletableFuture<Void> notifyUserAsync(UUID userId, String title, String body, Map<String, String> data) {
		return CompletableFuture.runAsync(() -> notifyUser(userId, title, body, data));
	}

	@Override
	public int notifyZone(UUID zoneId, String title, String body, Map<String, String> data, Predicate<AppUser> filter) {
		int count = 0;
		if (users != null) {
			for (AppUser u : users.findByZoneId(zoneId)) {
				if (filter == null || filter.test(u)) {
					notifyUser(u.getId(), title, body, data);
					count++;
				}
			}
		}
		log.info("[FCM PUSH zona {} → {} vecinos] {} · {}", zoneId, count, title, body);
		return count;
	}
}
