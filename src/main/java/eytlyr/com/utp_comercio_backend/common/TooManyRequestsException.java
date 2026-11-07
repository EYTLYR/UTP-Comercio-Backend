package eytlyr.com.utp_comercio_backend.common;

import org.springframework.http.HttpStatus;

/** 429 · demasiadas solicitudes o intentos en poco tiempo. */
public class TooManyRequestsException extends ApiException {

	public TooManyRequestsException(String code, String message) {
		super(code, message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.TOO_MANY_REQUESTS;
	}
}
