package eytlyr.com.utp_comercio_backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Resultado de un POST idempotente por clientId: created=false significa que ya existía
 * (el teléfono reintentó al recuperar la señal) y se responde 200 en vez de 201.
 */
public record Created<T>(T body, boolean created) {

	public ResponseEntity<T> toResponse() {
		return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(body);
	}
}
