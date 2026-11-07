package eytlyr.com.utp_comercio_backend.storage;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fotos en memoria (nearby.storage.provider=memory). Solo para pruebas: no escribe en disco ni en S3. */
@Component
@ConditionalOnProperty(prefix = "nearby.storage", name = "provider", havingValue = "memory")
public class MemoryStorageService implements StorageService {

	private final Map<String, byte[]> files = new ConcurrentHashMap<>();

	@Override
	public String store(byte[] bytes, String contentType) {
		String name = UUID.randomUUID() + ("image/png".equals(contentType) ? ".png" : ".jpg");
		files.put(name, bytes);
		return "/files/" + name;
	}

	@Override
	public Optional<StoredFile> load(String name) {
		return Optional.ofNullable(name == null ? null : files.get(name))
				.map(bytes -> new StoredFile(bytes, StorageService.contentTypeOf(name)));
	}
}
