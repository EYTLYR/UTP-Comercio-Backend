package eytlyr.com.utp_comercio_backend.notification;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.AppUserRepository;

/** Notificaciones de desarrollo: solo escriben en el log. */
@Component
@Primary
@ConditionalOnProperty(prefix = "nearby.notifications", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingNotificationService implements NotificationService {

	private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

	private final AppUserRepository users;

	public LoggingNotificationService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public void notifyUser(UUID userId, String title, String body, Map<String, String> data) {
		log.info("[PUSH → {}] {} · {} {}", userId, title, body, data);
	}

	@Override
	@Transactional(readOnly = true)
	public int notifyZone(UUID zoneId, String title, String body, Map<String, String> data, Predicate<AppUser> filter) {
		int count = 0;
		for (AppUser u : users.findByZoneId(zoneId)) {
			if (filter.test(u)) {
				count++;
			}
		}
		log.info("[PUSH zona {} → {} vecinos] {} · {} {}", zoneId, count, title, body, data);
		return count;
	}
}
