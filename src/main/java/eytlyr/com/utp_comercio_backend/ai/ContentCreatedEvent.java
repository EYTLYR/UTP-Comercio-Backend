package eytlyr.com.utp_comercio_backend.ai;

import java.util.UUID;

/** Se publica al crear o editar contenido (servicio, reseña, publicación, recurso). La IA lo procesa en la FASE 9. */
public record ContentCreatedEvent(SourceType sourceType, UUID sourceId) {
}
