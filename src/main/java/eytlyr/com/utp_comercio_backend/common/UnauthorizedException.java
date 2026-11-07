package eytlyr.com.utp_comercio_backend.common;

import org.springframework.http.HttpStatus;

/** 401 · credenciales inválidas (código SMS incorrecto, token de refresco vencido...). */
public class UnauthorizedException extends ApiException {

	public UnauthorizedException(String code, String message) {
		super(code, message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.UNAUTHORIZED;
	}
}
