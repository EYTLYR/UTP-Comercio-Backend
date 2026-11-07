package eytlyr.com.utp_comercio_backend.storage;

import java.util.Optional;

/** Almacenamiento de fotos. Local en dev (nearby.storage.local-dir), S3 en prod (preparado). */
public interface StorageService {

	record StoredFile(byte[] bytes, String contentType) {
	}

	/** @return la URL pública relativa, por ejemplo /files/abc.jpg */
	String store(byte[] bytes, String contentType);

	Optional<StoredFile> load(String name);
}
