package eytlyr.com.utp_comercio_backend.common;

import org.springframework.http.HttpStatus;

/** Excepción de negocio con estado HTTP y código estable para la app (por ejemplo "ENDORSEMENT_DUPLICATE"). */
public abstract class ApiException extends RuntimeException {

	private final String code;

	protected ApiException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public abstract HttpStatus getStatus();
}
