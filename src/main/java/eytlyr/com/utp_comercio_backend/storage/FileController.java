package eytlyr.com.utp_comercio_backend.storage;

import java.time.Duration;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eytlyr.com.utp_comercio_backend.common.NotFoundException;

@RestController
@Tag(name = "Archivos")
public class FileController {

	private final StorageService storage;

	public FileController(StorageService storage) {
		this.storage = storage;
	}

	@PostMapping(path = "/api/v1/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Subir foto (JPG/PNG máx. 5 MB) y obtener su URL relativa")
	public ResponseEntity<Map<String, String>> upload(@RequestPart("photo") MultipartFile photo) {
		byte[] bytes = PhotoUpload.read(photo);
		String url = storage.store(bytes, photo.getContentType());
		return ResponseEntity.ok(Map.of("url", url));
	}

	@GetMapping("/files/{name}")
	@SecurityRequirements
	@Operation(summary = "Foto subida (reportes de riesgo, publicaciones, perfiles)")
	public ResponseEntity<byte[]> file(@PathVariable String name) {
		var file = storage.load(name).orElseThrow(() -> NotFoundException.of("la foto"));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
				.body(file.bytes());
	}
}
