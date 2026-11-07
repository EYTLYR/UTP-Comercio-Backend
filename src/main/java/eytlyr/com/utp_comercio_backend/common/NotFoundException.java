package eytlyr.com.utp_comercio_backend.common;

import org.springframework.http.HttpStatus;

/** 404 · el recurso no existe. */
public class NotFoundException extends ApiException {

	public NotFoundException(String code, String message) {
		super(code, message);
	}

	/** Atajo: "No se encontró {what}." con código NOT_FOUND. */
	public static NotFoundException of(String what) {
		return new NotFoundException("NOT_FOUND", "No se encontró " + what + ".");
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.NOT_FOUND;
	}
}
