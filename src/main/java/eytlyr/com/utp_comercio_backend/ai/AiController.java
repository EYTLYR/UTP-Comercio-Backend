package eytlyr.com.utp_comercio_backend.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.ClassifyRequest;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.ClassifyResult;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.InventoryItemDto;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.InventorySnapshot;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.InventoryStats;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.NotifyResult;
import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.RescuePlanDto;
import eytlyr.com.utp_comercio_backend.auth.CurrentUser;

@RestController
@Tag(name = "IA · Protocolo Lázaro", description = "Inventario de supervivencia, misiones de rescate y micrófono inteligente")
public class AiController {

	private final InventoryService inventory;

	private final RescuePlanService rescue;

	private final CapabilityIndexer indexer;

	private final TextClassifier classifier;

	private final CurrentUser currentUser;

	public AiController(InventoryService inventory, RescuePlanService rescue, CapabilityIndexer indexer,
			TextClassifier classifier, CurrentUser currentUser) {
		this.inventory = inventory;
		this.rescue = rescue;
		this.indexer = indexer;
		this.classifier = classifier;
		this.currentUser = currentUser;
	}

	@GetMapping("/api/v1/ai/inventory")
	@Operation(summary = "Inventario de la zona (vulnerabilidades anónimas para vecinos)")
	public List<InventoryItemDto> inventory(@RequestParam(required = false) CapabilityKind kind) {
		return inventory.inventory(currentUser.id(), kind);
	}

	@GetMapping("/api/v1/ai/inventory/snapshot")
	@Operation(summary = "Inventario compacto para el teléfono (ETag; 304 si no cambió)")
	public ResponseEntity<InventorySnapshot> snapshot(
			@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
		InventorySnapshot snap = inventory.snapshot(currentUser.id());
		String etag = "\"" + snap.version() + "\"";
		if (etag.equals(ifNoneMatch)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
		}
		return ResponseEntity.ok().eTag(etag).body(snap);
	}

	@GetMapping("/api/v1/ai/inventory/stats")
	@Operation(summary = "Cuántos recursos, habilidades y alertas preventivas conoce la IA")
	public InventoryStats stats() {
		return inventory.stats(currentUser.id());
	}

	@PostMapping("/api/v1/ai/rescue-plans")
	@PreAuthorize("hasAnyRole('BRIGADIST', 'DELEGATE', 'MUNICIPALITY', 'ADMIN')")
	@Operation(summary = "Generar el plan de rescate: reportes preventivos y misiones automáticas")
	public RescuePlanDto generate() {
		return rescue.generate(inventory.zoneOf(currentUser.id()));
	}

	@GetMapping("/api/v1/ai/rescue-plans/latest")
	@PreAuthorize("hasAnyRole('BRIGADIST', 'DELEGATE', 'MUNICIPALITY', 'ADMIN')")
	@Operation(summary = "Último plan de rescate de mi zona")
	public RescuePlanDto latest() {
		return rescue.latest(inventory.zoneOf(currentUser.id()));
	}

	@PostMapping("/api/v1/ai/missions/{reportId}/notify")
	@PreAuthorize("hasAnyRole('BRIGADIST', 'DELEGATE', 'MUNICIPALITY', 'ADMIN')")
	@Operation(summary = "Avisar a los vecinos asignados y dejar el reporte en camino")
	public NotifyResult notifyMission(@PathVariable UUID reportId) {
		return rescue.notifyMission(reportId);
	}

	@PostMapping("/api/v1/ai/classify")
	@Operation(summary = "Micrófono inteligente: texto dictado → tipo, categoría, prioridad y ubicación")
	public ClassifyResult classify(@Valid @RequestBody ClassifyRequest body) {
		return classifier.classify(body.text(), body.mode());
	}

	@PostMapping("/api/v1/admin/ai/reindex")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Reprocesar todo el contenido de la zona con la IA")
	public Map<String, Integer> reindex() {
		return Map.of("capabilities", indexer.reindexZone(inventory.zoneOf(currentUser.id())));
	}
}
