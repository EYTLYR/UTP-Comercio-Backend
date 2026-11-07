package eytlyr.com.utp_comercio_backend.common;

import org.springframework.http.HttpStatus;

/** 422 · la petición es válida pero rompe una regla de negocio. */
public class BusinessRuleException extends ApiException {

	public BusinessRuleException(String code, String message) {
		super(code, message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.UNPROCESSABLE_CONTENT;
	}
}
