package eytlyr.com.utp_comercio_backend.sms.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import eytlyr.com.utp_comercio_backend.emergency.Priority;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.sos.SosType;

public final class SmsDtos {

	private SmsDtos() {
	}

	/** category (reporte vulnerable) o sosType (botón SOS); si no viene ninguno, se usa X (otra emergencia). */
	public record EncodeRequest(VulnerableCategory category, SosType sosType,
			@Min(value = 1, message = "Mínimo 1 persona") @Max(value = 9, message = "Máximo 9 personas") Integer people,
			String addressText, Double lat, Double lng, Priority priority) {
	}

	public record EncodeResult(String code, int length) {
	}

	public record DecodeRequest(@NotBlank(message = "Falta el código") String code) {
	}

	public record DecodeResult(String code, String type, String typeLabel, int people, String addressText, Double lat,
			Double lng, String priority) {
	}

	public record InboundSms(String from, String body) {
	}

	public record InboundResult(boolean received, boolean reportCreated, UUID reportId, DecodeResult decoded) {
	}
}
