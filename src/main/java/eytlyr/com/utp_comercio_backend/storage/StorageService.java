package eytlyr.com.utp_comercio_backend.storage;

import java.util.Optional;
import java.util.regex.Pattern;

/** Almacenamiento de fotos: S3 compatible (Neon) en dev y prod; en memoria solo en las pruebas. */
public interface StorageService {

	/** Solo nombres generados por store(): evita leer claves arbitrarias del bucket. */
	Pattern SAFE_NAME = Pattern.compile("^[a-f0-9-]{36}\\.(jpg|png)$");

	record StoredFile(byte[] bytes, String contentType) {
	}

	/** @return la URL pública relativa, por ejemplo /files/abc.jpg */
	String store(byte[] bytes, String contentType);

	Optional<StoredFile> load(String name);

	static String contentTypeOf(String name) {
		return name.endsWith(".png") ? "image/png" : "image/jpeg";
	}
}
