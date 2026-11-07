package eytlyr.com.utp_comercio_backend.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.common.TextUtils;

/** SMS de desarrollo: solo escribe en el log (celular enmascarado). */
@Component
@ConditionalOnProperty(prefix = "nearby.sms", name = "provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsGateway implements SmsGateway {

	private static final Logger log = LoggerFactory.getLogger(ConsoleSmsGateway.class);

	@Override
	public void send(String phone, String text) {
		// Sin datos personales en el log: el celular va enmascarado.
		log.info("[SMS → {}] {}", TextUtils.maskPhone(phone), text);
	}
}
