package eytlyr.com.utp_comercio_backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import eytlyr.com.utp_comercio_backend.sms.SmsGateway;

/** Envía cada aviso por separado: si uno falla, se registra y se sigue con los demás. */
@Component
public class DispatchListener {

	private static final Logger log = LoggerFactory.getLogger(DispatchListener.class);

	private final NotificationService notifications;

	private final SmsGateway sms;

	public DispatchListener(NotificationService notifications, SmsGateway sms) {
		this.notifications = notifications;
		this.sms = sms;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onDispatch(Dispatch d) {
		for (Dispatch.Push p : d.pushes()) {
			try {
				notifications.notifyUser(p.userId(), p.title(), p.body(), p.data());
			}
			catch (RuntimeException ex) {
				log.warn("No se pudo enviar el push a {}: {}", p.userId(), ex.getMessage());
			}
		}
		for (Dispatch.ZonePush z : d.zonePushes()) {
			try {
				notifications.notifyZone(z.zoneId(), z.title(), z.body(), z.data(), u -> true);
			}
			catch (RuntimeException ex) {
				log.warn("No se pudo avisar a la zona {}: {}", z.zoneId(), ex.getMessage());
			}
		}
		for (Dispatch.Sms s : d.sms()) {
			try {
				sms.send(s.phone(), s.text());
			}
			catch (RuntimeException ex) {
				log.warn("No se pudo enviar el SMS a {}: {}", s.phone(), ex.getMessage());
			}
		}
	}
}
