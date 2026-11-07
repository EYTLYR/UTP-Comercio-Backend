package eytlyr.com.utp_comercio_backend.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Respuesta paginada estándar de la API. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
		return from(page.map(mapper));
	}
}
