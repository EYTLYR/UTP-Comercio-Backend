package eytlyr.com.utp_comercio_backend.sos.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.sos.CheckInStatus;
import eytlyr.com.utp_comercio_backend.sos.ResponseStatus;
import eytlyr.com.utp_comercio_backend.sos.SosStatus;
import eytlyr.com.utp_comercio_backend.sos.SosType;

public final class SosDtos {

	private SosDtos() {
	}

	public record SosRequest(UUID clientId,
			@NotNull(message = "Falta la latitud") @DecimalMin("-90") @DecimalMax("90") Double lat,
			@NotNull(message = "Falta la longitud") @DecimalMin("-180") @DecimalMax("180") Double lng,
			SosType type,
			@Size(max = 500, message = "La nota no debe pasar de 500 caracteres") String note) {
	}

	public record SosCreatedDto(UUID id, SosStatus status, int notifiedNeighbors, int contactsNotified,
			int brigadeNotified, String smsCode, boolean zoneEmergencyActivated, OffsetDateTime createdAt) {
	}

	public record SosTypeRequest(@NotNull(message = "Elige el tipo de emergencia") SosType type) {
	}

	public record ResponderDto(UUID userId, String name, ResponseStatus status, Integer distanceM,
			Integer etaMinutes) {
	}

	public record SosDetailDto(UUID id, UUID userId, String userName, SosType type, SosStatus status, String note,
			double lat, double lng, String smsCode, OffsetDateTime createdAt, OffsetDateTime closedAt,
			List<ResponderDto> responders) {
	}

	public record NearbySosDto(UUID id, UUID userId, String userName, String userPhone, SosType type, int distanceM, long minutesAgo,
			long goingCount, double approxLat, double approxLng) {
	}

	public record SosContactDto(UUID conversationId, UUID userId, String userName, String phone) {
	}

	public record SosResponseRequest(@NotNull(message = "Indica GOING, DECLINED o ARRIVED") ResponseStatus status) {
	}

	public record CheckInRequest(@NotNull(message = "Indica SAFE o NEED_HELP") CheckInStatus status) {
	}

	public record CheckInDto(UUID id, CheckInStatus status, int contactsNotified, OffsetDateTime createdAt) {
	}
}
