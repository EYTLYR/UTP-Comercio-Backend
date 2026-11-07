package eytlyr.com.utp_comercio_backend.storage;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fotos en S3 (nearby.storage.provider=s3). Preparado, sin implementar.
 * Para activarlo: agregar el SDK de AWS, leer el bucket y la región de variables de entorno y devolver
 * una URL firmada o la del CDN.
 */
@Component
@ConditionalOnProperty(prefix = "nearby.storage", name = "provider", havingValue = "s3")
public class S3StorageService implements StorageService {

	@Override
	public String store(byte[] bytes, String contentType) {
		throw new UnsupportedOperationException("El almacenamiento en S3 aún no está implementado.");
	}

	@Override
	public Optional<StoredFile> load(String name) {
		return Optional.empty();
	}
}
