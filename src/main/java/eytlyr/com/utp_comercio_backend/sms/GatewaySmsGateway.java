package eytlyr.com.utp_comercio_backend.sms;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SMS reales por el microservicio SMS (micro/sms-gateway, nearby.sms.provider=gateway). El microservicio
 * controla un celular Android con SMS Gateway; el backend solo le pide el envío por HTTP.
 * Contrato: POST {url}/v1/messages con X-Api-Key y {"to": "987654321", "text": "..."} → 202.
 */
@Component
@ConditionalOnProperty(prefix = "nearby.sms", name = "provider", havingValue = "gateway")
public class GatewaySmsGateway implements SmsGateway {

	private static final Logger log = LoggerFactory.getLogger(GatewaySmsGateway.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private final URI endpoint;

	private final String apiKey;

	@Autowired
	public GatewaySmsGateway(NearbyProperties props) {
		this(props.sms().gateway());
	}

	GatewaySmsGateway(NearbyProperties.Sms.Gateway g) {
		if (g == null || blank(g.url()) || blank(g.apiKey())) {
			throw new IllegalStateException(
					"Microservicio SMS: define NEARBY_SMS_GATEWAY_URL y NEARBY_SMS_GATEWAY_API_KEY en .env, o usa NEARBY_SMS_PROVIDER=console");
		}
		String base = g.url().trim().replaceAll("/+$", "");
		this.endpoint = URI.create(base + "/v1/messages");
		this.apiKey = g.apiKey().trim();
		log.info("SMS por el microservicio {}", base);
	}

	/** @param phone celular peruano de 9 dígitos (sin +51; el microservicio lo agrega) */
	@Override
	public void send(String phone, String text) {
		HttpRequest request = HttpRequest.newBuilder(endpoint)
				.timeout(Duration.ofSeconds(10))
				.header("X-Api-Key", apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("to", phone, "text", text))))
				.build();
		HttpResponse<String> res;
		try {
			res = http.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Envío de SMS interrumpido", ex);
		}
		catch (Exception ex) {
			throw new IllegalStateException("El microservicio SMS no respondió: " + ex.getMessage(), ex);
		}
		if (res.statusCode() / 100 != 2) {
			throw new IllegalStateException("El microservicio SMS rechazó el envío a " + TextUtils.maskPhone(phone) + " ("
					+ res.statusCode() + "): " + error(res.body()));
		}
		log.info("[SMS → {}] en cola del microservicio", TextUtils.maskPhone(phone));
	}

	private static String error(String body) {
		try {
			JsonNode n = JSON.readTree(body);
			return n.path("error").asText(body);
		}
		catch (RuntimeException ex) {
			return body;
		}
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}
