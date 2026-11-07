package eytlyr.com.utp_comercio_backend.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Envío real de SMS con Twilio (nearby.sms.provider=twilio). Preparado pero sin implementar.
 * Para activarlo: agregar el SDK de Twilio, leer TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_FROM
 * de variables de entorno y enviar a "+51" + phone.
 */
@Component
@ConditionalOnProperty(prefix = "nearby.sms", name = "provider", havingValue = "twilio")
public class TwilioSmsGateway implements SmsGateway {

	@Override
	public void send(String phone, String text) {
		throw new UnsupportedOperationException("El envío por Twilio aún no está implementado.");
	}
}
