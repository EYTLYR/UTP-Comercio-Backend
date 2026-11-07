package eytlyr.com.utp_comercio_backend.storage;

import java.time.Duration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.common.NotFoundException;

@RestController
@Tag(name = "Archivos")
public class FileController {

	private final StorageService storage;

	public FileController(StorageService storage) {
		this.storage = storage;
	}

	@GetMapping("/files/{name}")
	@SecurityRequirements
	@Operation(summary = "Foto subida (reportes de riesgo)")
	public ResponseEntity<byte[]> file(@PathVariable String name) {
		var file = storage.load(name).orElseThrow(() -> NotFoundException.of("la foto"));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
				.body(file.bytes());
	}
}
