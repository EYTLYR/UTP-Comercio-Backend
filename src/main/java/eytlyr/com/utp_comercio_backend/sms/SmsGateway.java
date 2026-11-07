package eytlyr.com.utp_comercio_backend.sms;

/** Envío de SMS. La implementación se elige con nearby.sms.provider (console por defecto). */
public interface SmsGateway {

	/**
	 * Envía un SMS.
	 * @param phone celular peruano de 9 dígitos (sin +51)
	 * @param text texto del mensaje
	 */
	void send(String phone, String text);
}
