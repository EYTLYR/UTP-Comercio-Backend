package eytlyr.com.utp_comercio_backend.sms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.common.UnauthorizedException;
import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.InboundResult;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.InboundSms;

/**
 * Webhook de Twilio para SMS entrantes ("A message comes in" del número de Twilio).
 * Twilio manda un formulario (From, Body...) firmado con X-Twilio-Signature; se responde TwiML.
 */
@RestController
@Tag(name = "SOS por SMS")
public class TwilioWebhookController {

	private final SmsService sms;

	private final NearbyProperties.Sms.Twilio twilio;

	public TwilioWebhookController(SmsService sms, NearbyProperties props) {
		this.sms = sms;
		this.twilio = props.sms().twilio();
	}

	@PostMapping(path = "/api/v1/sms/inbound/twilio", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
			produces = MediaType.APPLICATION_XML_VALUE)
	@SecurityRequirements
	@Operation(summary = "Webhook de Twilio: SMS entrante con un código NB#SOS! (firma X-Twilio-Signature)")
	public ResponseEntity<String> inbound(@RequestParam MultiValueMap<String, String> form,
			@RequestHeader(value = "X-Twilio-Signature", required = false) String signature, HttpServletRequest request) {
		String token = twilio == null ? null : twilio.authToken();
		if (token == null || token.isBlank() || signature == null
				|| !valid(token, webhookUrl(request), form.toSingleValueMap(), signature)) {
			throw new UnauthorizedException("SMS_SIGNATURE_INVALID", "Firma de Twilio inválida.");
		}
		InboundResult result = sms.receive(new InboundSms(form.getFirst("From"), form.getFirst("Body")));
		String reply = result.reportCreated()
				? "<Message>Nearby recibió tu SOS. La brigada de tu zona ya fue avisada.</Message>"
				: "";
		return ResponseEntity.ok()
				.contentType(new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8))
				.body("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response>" + reply + "</Response>");
	}

	/** La URL que Twilio firmó: la configurada (recomendado detrás de un proxy) o la de la petición. */
	private String webhookUrl(HttpServletRequest request) {
		if (twilio != null && twilio.webhookUrl() != null && !twilio.webhookUrl().isBlank()) {
			return twilio.webhookUrl().trim();
		}
		String query = request.getQueryString();
		return request.getRequestURL() + (query != null ? "?" + query : "");
	}

	/**
	 * Algoritmo de Twilio: Base64(HMAC-SHA1(authToken, url + cada parámetro POST ordenado como nombre+valor)).
	 * Los parámetros de la query ya van dentro de la URL, por eso se quitan del formulario.
	 */
	static boolean valid(String authToken, String url, Map<String, String> params, String signature) {
		String expected = sign(authToken, url, withoutQueryParams(url, params));
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
				signature.getBytes(StandardCharsets.UTF_8));
	}

	static String sign(String authToken, String url, Map<String, String> params) {
		StringBuilder data = new StringBuilder(url);
		new TreeMap<>(params).forEach((k, v) -> data.append(k).append(v == null ? "" : v));
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
			return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException("No se pudo calcular la firma de Twilio", ex);
		}
	}

	private static Map<String, String> withoutQueryParams(String url, Map<String, String> params) {
		int q = url.indexOf('?');
		if (q < 0) {
			return params;
		}
		Map<String, String> copy = new TreeMap<>(params);
		for (String pair : url.substring(q + 1).split("&")) {
			int eq = pair.indexOf('=');
			copy.remove(java.net.URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8));
		}
		return copy;
	}
}
