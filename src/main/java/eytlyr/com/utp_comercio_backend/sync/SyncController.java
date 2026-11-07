package eytlyr.com.utp_comercio_backend.sync;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.OutboxItem;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.OutboxResult;
import eytlyr.com.utp_comercio_backend.sync.dto.SyncDtos.SyncResponse;

@RestController
@Tag(name = "Sin conexión", description = "Sincronizar la zona y enviar la cola del teléfono al volver la señal")
public class SyncController {

	private final SyncService sync;

	private final OutboxProcessor outbox;

	private final CurrentUser currentUser;

	public SyncController(SyncService sync, OutboxProcessor outbox, CurrentUser currentUser) {
		this.sync = sync;
		this.outbox = outbox;
		this.currentUser = currentUser;
	}

	@GetMapping("/api/v1/sync")
	@Operation(summary = "Datos de la zona para guardar en el teléfono (since = solo lo cambiado)")
	public SyncResponse sync(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since) {
		return sync.sync(currentUser.id(), since);
	}

	@PostMapping("/api/v1/sync/outbox")
	@Operation(summary = "Enviar la cola: POST, SOS, VULNERABLE_REPORT, RISK_REPORT, MESSAGE o CHECK_IN (en orden)")
	public List<OutboxResult> outbox(
			@RequestBody @Size(max = 100, message = "Máximo 100 elementos por envío") List<@Valid OutboxItem> items) {
		return outbox.process(currentUser.id(), items);
	}
}
