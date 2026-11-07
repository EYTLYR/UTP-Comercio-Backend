package eytlyr.com.utp_comercio_backend.common;

import org.springframework.http.HttpStatus;

/** 409 · el recurso ya existe o choca con otro. */
public class ConflictException extends ApiException {

	public ConflictException(String code, String message) {
		super(code, message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.CONFLICT;
	}
}
