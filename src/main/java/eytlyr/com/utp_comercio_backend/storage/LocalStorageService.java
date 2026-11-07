package eytlyr.com.utp_comercio_backend.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.config.NearbyProperties;

/** Fotos en disco (desarrollo). Se sirven en GET /files/{nombre}. */
@Component
@ConditionalOnProperty(prefix = "nearby.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

	private static final Pattern SAFE_NAME = Pattern.compile("^[a-f0-9-]{36}\\.(jpg|png)$");

	private final Path dir;

	public LocalStorageService(NearbyProperties props) {
		this.dir = Path.of(props.storage().localDir()).toAbsolutePath().normalize();
	}

	@Override
	public String store(byte[] bytes, String contentType) {
		String name = UUID.randomUUID() + ("image/png".equals(contentType) ? ".png" : ".jpg");
		try {
			Files.createDirectories(dir);
			Files.write(dir.resolve(name), bytes);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("No se pudo guardar la foto", ex);
		}
		return "/files/" + name;
	}

	/** Solo nombres generados por store(): evita leer archivos fuera de la carpeta. */
	@Override
	public Optional<StoredFile> load(String name) {
		if (name == null || !SAFE_NAME.matcher(name).matches()) {
			return Optional.empty();
		}
		Path file = dir.resolve(name);
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		try {
			return Optional.of(new StoredFile(Files.readAllBytes(file), name.endsWith(".png") ? "image/png" : "image/jpeg"));
		}
		catch (IOException ex) {
			return Optional.empty();
		}
	}
}
