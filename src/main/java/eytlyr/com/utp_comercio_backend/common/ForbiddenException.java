package eytlyr.com.utp_comercio_backend.common;

import org.springframework.http.HttpStatus;

/** 403 · el usuario no puede hacer esta acción. */
public class ForbiddenException extends ApiException {

	public ForbiddenException(String code, String message) {
		super(code, message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.FORBIDDEN;
	}
}
