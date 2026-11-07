package eytlyr.com.utp_comercio_backend.notification;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import eytlyr.com.utp_comercio_backend.user.AppUser;

/** Notificaciones push. La implementación se elige con nearby.notifications.provider (log por defecto). */
public interface NotificationService {

	void notifyUser(UUID userId, String title, String body, Map<String, String> data);

	/** @return cuántos vecinos fueron notificados */
	int notifyZone(UUID zoneId, String title, String body, Map<String, String> data, Predicate<AppUser> filter);
}
