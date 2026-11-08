package eytlyr.com.utp_comercio_backend.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import eytlyr.com.utp_comercio_backend.user.AppUser;
import eytlyr.com.utp_comercio_backend.user.BrigadeRequest;
import eytlyr.com.utp_comercio_backend.user.Role;

/** Solicitudes de brigadista y roles que administra la Municipalidad. */
public final class RoleDtos {

	private RoleDtos() {
	}

	public record BrigadeRequestCreate(@Size(max = 300, message = "Cuéntalo en 300 caracteres o menos") String skills) {
	}

	/** status NONE cuando el vecino nunca lo pidió. */
	public record BrigadeRequestDto(UUID id, UUID userId, String name, String phone, String photoUrl, String skills,
			String status, OffsetDateTime createdAt, OffsetDateTime decidedAt) {

		public static final BrigadeRequestDto NONE = new BrigadeRequestDto(null, null, null, null, null, null, "NONE",
				null, null);

		/** Debe llamarse dentro de una transacción (el usuario es LAZY). */
		public static BrigadeRequestDto from(BrigadeRequest r) {
			AppUser u = r.getUser();
			return new BrigadeRequestDto(r.getId(), u.getId(), u.getName(), u.getPhone(), u.getPhotoUrl(), r.getSkills(),
					r.getStatus().name(), r.getCreatedAt(), r.getDecidedAt());
		}
	}

	public record RoleChangeRequest(@NotNull(message = "Indica el rol: NEIGHBOR, BRIGADIST o DELEGATE") Role role) {
	}

	/** Vecino buscado por celular para cambiarle el rol. */
	public record UserSummaryDto(UUID id, String name, String phone, Role role, String photoUrl, String zoneName) {

		public static UserSummaryDto from(AppUser u) {
			return new UserSummaryDto(u.getId(), u.getName(), u.getPhone(), u.getRole(), u.getPhotoUrl(),
					u.getZone() != null ? u.getZone().getName() : null);
		}
	}
}
