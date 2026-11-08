package eytlyr.com.utp_comercio_backend.sms;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SMS reales por HTTP al microservicio SMS (micro/sms-gateway). Se activa con nearby.sms.provider=http
 * (también acepta "gateway", el nombre anterior, para no romper los .env existentes).
 * El microservicio controla un celular Android con SMS Gateway; el backend solo le pide el envío.
 *
 * Contrato: POST {url}/v1/messages con X-Api-Key y {"to": "987654321", "text": "..."} → 202.
 * En el SOS, el texto lleva el enlace del mapa y el código comprimido de SosCodec (NB#SOS!...).
 *
 * Reintentos: si el microservicio no responde, tarda o devuelve 5xx/429, reintenta 2 veces (3 intentos en
 * total, con espera creciente). Un 4xx (clave inválida, número mal formado) no se reintenta. Si todos
 * fallan, deja un log de error "CRÍTICO" y lanza IllegalStateException; quien envía los avisos del SOS
 * ({@code DispatchListener}) lo hace después del commit y en segundo plano, así que el SOS ya quedó guardado.
 */
@Component
@ConditionalOnExpression("'${nearby.sms.provider:console}' == 'http' or '${nearby.sms.provider:console}' == 'gateway'")
public class HttpSmsGateway implements SmsGateway {

	private static final Logger log = LoggerFactory.getLogger(HttpSmsGateway.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	static final int RETRIES = 2;

	private final RestClient http;

	private final String apiKey;

	private final Duration backoff;

	@Autowired
	public HttpSmsGateway(NearbyProperties props) {
		this(props.sms().gateway(), Duration.ofMillis(500));
	}

	HttpSmsGateway(NearbyProperties.Sms.Gateway g, Duration backoff) {
		if (g == null || blank(g.url()) || blank(g.apiKey())) {
			throw new IllegalStateException(
					"Microservicio SMS: define NEARBY_SMS_GATEWAY_URL y NEARBY_SMS_GATEWAY_API_KEY en .env, o usa NEARBY_SMS_PROVIDER=console");
		}
		String base = g.url().trim().replaceAll("/+$", "");
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(10));
		this.http = RestClient.builder().baseUrl(base).requestFactory(factory).build();
		this.apiKey = g.apiKey().trim();
		this.backoff = backoff;
		log.info("SMS por el microservicio {}", base);
	}

	/** @param phone celular peruano de 9 dígitos (sin +51; el microservicio lo agrega) */
	@Override
	public void send(String phone, String text) {
		String body = JSON.writeValueAsString(Map.of("to", phone, "text", text));
		RuntimeException last = null;
		for (int attempt = 1; attempt <= RETRIES + 1; attempt++) {
			try {
				String responseBody = http.post()
						.uri("/v1/messages")
						.header("X-Api-Key", apiKey)
						.contentType(MediaType.APPLICATION_JSON)
						.body(body)
						.retrieve()
						.body(String.class);
				log.info("[SMS → {}] en cola del microservicio{}", TextUtils.maskPhone(phone),
						attempt > 1 ? " (intento " + attempt + ")" : "");

				// Verificar si el microservicio o chip celular reportó fallo inmediato (ej. falta de saldo)
				if (responseBody != null && !responseBody.isBlank()) {
					try {
						JsonNode node = JSON.readTree(responseBody);
						String msgId = node.path("id").asText(null);
						if (msgId != null && !msgId.isBlank()) {
							pause(Duration.ofMillis(700));
							String statusBody = http.get()
									.uri("/api/status/" + msgId)
									.header("X-Api-Key", apiKey)
									.retrieve()
									.body(String.class);
							if (statusBody != null && !statusBody.isBlank()) {
								JsonNode statusNode = JSON.readTree(statusBody);
								String state = statusNode.path("state").asText("");
								if ("Failed".equalsIgnoreCase(state)) {
									String err = statusNode.path("recipients").path(0).path("error")
											.asText("Fallo de entrega en el chip del dispositivo");
									log.warn("El dispositivo SMS reportó fallo de envío para {}: {}",
											TextUtils.maskPhone(phone), err);
									throw new IllegalStateException("El chip celular no pudo enviar el SMS: " + err);
								}
							}
						}
					}
					catch (IllegalStateException ex) {
						throw ex;
					}
					catch (Exception ignored) {
						// Si falla la consulta de verificación no interrumpir
					}
				}
				return;
			}
			catch (RestClientResponseException ex) {
				int status = ex.getStatusCode().value();
				last = new IllegalStateException("El microservicio SMS rechazó el envío a " + TextUtils.maskPhone(phone)
						+ " (" + status + "): " + error(ex.getResponseBodyAsString()), ex);
				if (status != 429 && status < 500) {
					break; // error del pedido: reintentar no cambia nada
				}
			}
			catch (RuntimeException ex) {
				last = new IllegalStateException("El microservicio SMS no respondió: " + ex.getMessage(), ex);
			}
			if (attempt <= RETRIES) {
				log.warn("SMS a {}: intento {} falló ({}); se reintenta.", TextUtils.maskPhone(phone), attempt,
						last.getMessage());
				pause(backoff.multipliedBy(attempt));
			}
		}
		log.error("CRÍTICO: no se pudo enviar el SMS a {} por el microservicio: {}", TextUtils.maskPhone(phone),
				last.getMessage());
		throw last;
	}

	private static void pause(Duration d) {
		try {
			Thread.sleep(d.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Envío de SMS interrumpido", ex);
		}
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
