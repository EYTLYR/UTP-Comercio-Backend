package eytlyr.com.utp_comercio_backend.sms;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.DecodeRequest;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.DecodeResult;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.EncodeRequest;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.EncodeResult;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.InboundResult;
import eytlyr.com.utp_comercio_backend.sms.dto.SmsDtos.InboundSms;

@RestController
@Tag(name = "SOS por SMS", description = "Código comprimido NB#SOS! para enviar sin internet (SMS o sonido)")
public class SmsController {

	private final SmsService sms;

	private final CurrentUser currentUser;

	public SmsController(SmsService sms, CurrentUser currentUser) {
		this.sms = sms;
		this.currentUser = currentUser;
	}

	@PostMapping("/api/v1/sms/encode")
	@Operation(summary = "Comprimir un reporte en un código de 25 caracteres o menos")
	public EncodeResult encode(@Valid @RequestBody EncodeRequest body) {
		return sms.encode(currentUser.id(), body);
	}

	@PostMapping("/api/v1/sms/decode")
	@Operation(summary = "Leer un código (vista del brigadista). 422 SMS_CODE_INVALID si no es válido")
	public DecodeResult decode(@Valid @RequestBody DecodeRequest body) {
		return sms.decode(currentUser.id(), body.code());
	}

	@PostMapping("/api/v1/sms/inbound")
	@SecurityRequirements
	@Operation(summary = "Webhook de la pasarela SMS (header X-Nearby-Token)")
	public InboundResult inbound(@RequestHeader(value = "X-Nearby-Token", required = false) String token,
			@RequestBody InboundSms body) {
		return sms.inbound(token, body);
	}
}
