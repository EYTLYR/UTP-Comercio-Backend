package eytlyr.com.utp_comercio_backend.municipality.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.municipality.Municipality;
import eytlyr.com.utp_comercio_backend.user.AppUser;

/** Registro de Municipalidades, su aprobación y su equipo. */
public final class MunicipalityDtos {

	private MunicipalityDtos() {
	}

	public record MunicipalityRegistration(
			@NotBlank(message = "Escribe el nombre de la Municipalidad") @Size(max = 160) String name,
			@NotBlank(message = "Indica el distrito") @Size(max = 120) String district,
			@NotBlank(message = "Escribe el RUC")
			@Pattern(regexp = "^20\\d{9}$", message = "El RUC de una Municipalidad tiene 11 dígitos y empieza con 20") String ruc,
			@NotBlank(message = "Escribe el nombre del responsable") @Size(max = 120) String contactName,
			@Size(max = 120) String contactPosition,
			@NotBlank(message = "Escribe el correo institucional")
			@Email(message = "Revisa el correo institucional") @Size(max = 160) String email) {
	}

	public record RejectRequest(@Size(max = 300, message = "El motivo no debe pasar de 300 caracteres") String reason) {
	}

	public record StaffRequest(@NotBlank(message = "Escribe el celular") String phone) {
	}

	/** status NONE cuando la persona nunca registró una Municipalidad. */
	public record MunicipalityDto(UUID id, String name, String district, String ruc, String contactName,
			String contactPosition, String email, String status, String rejectReason, String requestedByName,
			String requestedByPhone, OffsetDateTime createdAt, OffsetDateTime decidedAt) {

		public static final MunicipalityDto NONE = new MunicipalityDto(null, null, null, null, null, null, null, "NONE",
				null, null, null, null, null);

		/** Debe llamarse dentro de una transacción (requestedBy es LAZY). */
		public static MunicipalityDto from(Municipality m) {
			AppUser by = m.getRequestedBy();
			return new MunicipalityDto(m.getId(), m.getName(), m.getDistrict(), m.getRuc(), m.getContactName(),
					m.getContactPosition(), m.getEmail(), m.getStatus().name(), m.getRejectReason(), by.getName(),
					by.getPhone(), m.getCreatedAt(), m.getDecidedAt());
		}
	}

	/** Funcionario del equipo de una Municipalidad. */
	public record StaffDto(UUID id, String name, String phone, String photoUrl, boolean me) {
	}
}
