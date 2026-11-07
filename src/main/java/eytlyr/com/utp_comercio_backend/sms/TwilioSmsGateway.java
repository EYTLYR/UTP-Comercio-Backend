package eytlyr.com.utp_comercio_backend.sms;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Envío real de SMS con la API REST de Twilio (nearby.sms.provider=twilio), sin SDK.
 * Necesita TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_FROM (número de Twilio en formato +1...).
 * Con una cuenta de prueba solo llega a números verificados en la consola de Twilio.
 */
@Component
@ConditionalOnProperty(prefix = "nearby.sms", name = "provider", havingValue = "twilio")
public class TwilioSmsGateway implements SmsGateway {

	private static final Logger log = LoggerFactory.getLogger(TwilioSmsGateway.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private final String accountSid;

	private final String authHeader;

	private final String from;

	public TwilioSmsGateway(NearbyProperties props) {
		NearbyProperties.Sms.Twilio t = props.sms().twilio();
		if (t == null || blank(t.accountSid()) || blank(t.authToken()) || blank(t.from())) {
			throw new IllegalStateException(
					"Twilio: define TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_FROM en .env, o usa NEARBY_SMS_PROVIDER=console");
		}
		this.accountSid = t.accountSid().trim();
		this.from = t.from().trim();
		this.authHeader = "Basic " + Base64.getEncoder()
				.encodeToString((accountSid + ":" + t.authToken().trim()).getBytes(StandardCharsets.UTF_8));
		log.info("SMS con Twilio desde {}", from);
	}

	/** @param phone celular peruano de 9 dígitos (sin +51) */
	@Override
	public void send(String phone, String text) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("To", toE164(phone));
		form.put("From", from);
		form.put("Body", text);
		String body = form.entrySet().stream()
				.map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
						+ URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
				.timeout(Duration.ofSeconds(10))
				.header("Authorization", authHeader)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		HttpResponse<String> res;
		try {
			res = http.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Envío por Twilio interrumpido", ex);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Twilio no respondió: " + ex.getMessage(), ex);
		}
		if (res.statusCode() / 100 != 2) {
			throw new IllegalStateException("Twilio rechazó el SMS a " + TextUtils.maskPhone(phone) + ": " + twilioError(res.body()));
		}
		log.info("[SMS Twilio → {}] enviado", TextUtils.maskPhone(phone));
	}

	static String toE164(String phone) {
		String digits = phone == null ? "" : phone.replaceAll("\\D", "");
		if (phone != null && phone.trim().startsWith("+")) {
			return "+" + digits;
		}
		return digits.length() == 9 ? "+51" + digits : "+" + digits;
	}

	/** Twilio responde {"code": 21608, "message": "..."}; 21608 = número no verificado en cuenta de prueba. */
	private static String twilioError(String body) {
		try {
			JsonNode n = JSON.readTree(body);
			return n.path("code").asText("") + " " + n.path("message").asText(body);
		}
		catch (RuntimeException ex) {
			return body;
		}
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
