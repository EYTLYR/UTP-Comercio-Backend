package eytlyr.com.utp_comercio_backend.common;

import java.util.List;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejo global de errores. Siempre responde ProblemDetail (RFC 9457) con dos campos extra:
 * "code" (estable, para la app) y "errors" (solo en validación: [{field, message}]).
 */
@RestControllerAdvice
public class ApiError extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiError.class);

	public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

	public record FieldMessage(String field, String message) {
	}

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApi(ApiException ex) {
		return problem(ex.getStatus(), titleFor(ex.getStatus()), ex.getMessage(), ex.getCode());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permiso para esta acción.", "FORBIDDEN");
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
		List<FieldMessage> errors = ex.getConstraintViolations().stream()
				.map(v -> new FieldMessage(lastNode(v.getPropertyPath().toString()), v.getMessage()))
				.toList();
		return validation(errors);
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Error inesperado", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
				"Ocurrió un error inesperado. Inténtalo de nuevo en unos segundos.", "INTERNAL_ERROR");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldMessage> errors = ex.getBindingResult().getAllErrors().stream()
				.map(e -> new FieldMessage(e instanceof FieldError fe ? fe.getField() : e.getObjectName(),
						e.getDefaultMessage()))
				.toList();
		return ResponseEntity.badRequest().body(validation(errors));
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldMessage> errors = ex.getParameterValidationResults().stream()
				.flatMap(r -> r.getResolvableErrors().stream()
						.map(e -> new FieldMessage(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
				.toList();
		return ResponseEntity.badRequest().body(validation(errors));
	}

	/**
	 * Errores estándar de Spring MVC (JSON mal formado, método no permitido, ruta inexistente...):
	 * añade "code" y traduce el texto al español.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail pd && pd.getProperties() == null) {
			HttpStatus status = HttpStatus.resolve(statusCode.value());
			pd.setProperty("code", status != null ? status.name() : "ERROR");
			if (status != null) {
				switch (status) {
					case BAD_REQUEST -> spanish(pd, "Petición inválida", "La petición no tiene el formato esperado.");
					case NOT_FOUND -> spanish(pd, "No encontrado", "La ruta solicitada no existe.");
					case METHOD_NOT_ALLOWED -> spanish(pd, "Método no permitido", "Esta ruta no acepta ese método HTTP.");
					case UNSUPPORTED_MEDIA_TYPE -> spanish(pd, "Formato no soportado", "Envía el cuerpo como JSON.");
					case NOT_ACCEPTABLE -> spanish(pd, "Formato no aceptable", "La API responde en JSON.");
					case PAYLOAD_TOO_LARGE -> spanish(pd, "Archivo muy grande", "El archivo supera el tamaño permitido.");
					default -> { }
				}
			}
		}
		return response;
	}

	private static void spanish(ProblemDetail pd, String title, String detail) {
		pd.setTitle(title);
		pd.setDetail(detail);
	}

	private static ProblemDetail validation(List<FieldMessage> errors) {
		// Con un solo error, el detalle es ese mensaje: la app puede mostrarlo tal cual.
		String detail = errors.size() == 1 ? errors.getFirst().message() : "Revisa los campos marcados.";
		ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Datos inválidos", detail, VALIDATION_ERROR);
		pd.setProperty("errors", errors);
		return pd;
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setTitle(title);
		pd.setProperty("code", code);
		return pd;
	}

	private static String titleFor(HttpStatus status) {
		return switch (status) {
			case NOT_FOUND -> "No encontrado";
			case CONFLICT -> "Conflicto";
			case FORBIDDEN -> "Acceso denegado";
			case UNPROCESSABLE_CONTENT -> "Regla de negocio";
			case UNAUTHORIZED -> "No autenticado";
			case TOO_MANY_REQUESTS -> "Demasiados intentos";
			default -> status.getReasonPhrase();
		};
	}

	private static String lastNode(String path) {
		int i = path.lastIndexOf('.');
		return i >= 0 ? path.substring(i + 1) : path;
	}
}
