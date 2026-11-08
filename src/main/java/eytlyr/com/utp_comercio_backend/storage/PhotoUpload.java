package eytlyr.com.utp_comercio_backend.storage;

import java.io.IOException;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;

/** Reglas comunes para subir una foto: JPG o PNG de hasta 5 MB. */
public final class PhotoUpload {

	public static final String PHOTO_INVALID = "PHOTO_INVALID";

	public static final long MAX_BYTES = 5L * 1024 * 1024;

	public static final Set<String> TYPES = Set.of("image/jpeg", "image/png");

	private PhotoUpload() {
	}

	/** @return los bytes de la foto, o 422 PHOTO_INVALID si falta, pesa más de 5 MB o no es JPG/PNG. */
	public static byte[] read(MultipartFile photo) {
		if (photo == null || photo.isEmpty()) {
			throw new BusinessRuleException(PHOTO_INVALID, "Elige una foto.");
		}
		if (photo.getSize() > MAX_BYTES) {
			throw new BusinessRuleException(PHOTO_INVALID, "La foto no debe pasar de 5 MB.");
		}
		if (photo.getContentType() == null || !TYPES.contains(photo.getContentType())) {
			throw new BusinessRuleException(PHOTO_INVALID, "La foto debe ser JPG o PNG.");
		}
		try {
			return photo.getBytes();
		}
		catch (IOException ex) {
			throw new BusinessRuleException(PHOTO_INVALID, "No se pudo leer la foto.");
		}
	}
}
