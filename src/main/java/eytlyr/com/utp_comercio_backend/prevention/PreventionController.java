package eytlyr.com.utp_comercio_backend.prevention;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.emergency.ListingKind;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceDto;
import eytlyr.com.utp_comercio_backend.emergency.dto.EmergencyDtos.ResourceSummaryDto;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.AnalyzeResult;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.CoordinateResult;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.HeatSpot;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.MitigationRequest;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskReportCreated;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskReportDto;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskReportRequest;
import eytlyr.com.utp_comercio_backend.prevention.dto.PreventionDtos.RiskZoneDto;

@RestController
@Tag(name = "El Niño · Radar preventivo", description = "Reportes de riesgo, radar con IA, mapa de calor y mitigación")
public class PreventionController {

	private final PreventionService prevention;

	private final CurrentUser currentUser;

	public PreventionController(PreventionService prevention, CurrentUser currentUser) {
		this.prevention = prevention;
		this.currentUser = currentUser;
	}

	@PostMapping(path = "/api/v1/risk-reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Reportar un riesgo con foto (multipart: data JSON + photo jpg/png máx. 5 MB)")
	public ResponseEntity<RiskReportCreated> createWithPhoto(@Valid @RequestPart("data") RiskReportRequest data,
			@RequestPart(value = "photo", required = false) MultipartFile photo) {
		return prevention.createReport(currentUser.id(), data, photo).toResponse();
	}

	@PostMapping(path = "/api/v1/risk-reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Reportar un riesgo sin foto (JSON). escalated=true si la zona subió a RED")
	public ResponseEntity<RiskReportCreated> create(@Valid @RequestBody RiskReportRequest data) {
		return prevention.createReport(currentUser.id(), data, null).toResponse();
	}

	@GetMapping("/api/v1/risk-reports")
	@Operation(summary = "Reportes de riesgo recientes de mi zona")
	public List<RiskReportDto> recent(@RequestParam(defaultValue = "6") int hours) {
		return prevention.recentReports(currentUser.id(), hours);
	}

	@PostMapping("/api/v1/risk-zones/analyze")
	@Operation(summary = "Analizar con IA: recalcula las zonas de riesgo")
	public AnalyzeResult analyze() {
		return prevention.analyze(currentUser.id());
	}

	@GetMapping("/api/v1/risk-zones")
	@Operation(summary = "Zonas de riesgo activas con patrón, alerta y mitigación sugerida")
	public List<RiskZoneDto> zones() {
		return prevention.zones(currentUser.id());
	}

	@PostMapping("/api/v1/risk-zones/{id}/coordinate")
	@Operation(summary = "Sí, coordinar ayuda: abre el chat con quien tiene los recursos y avisa a la manzana")
	public CoordinateResult coordinate(@PathVariable UUID id) {
		return prevention.coordinate(currentUser.id(), id);
	}

	@GetMapping("/api/v1/map/heat")
	@Operation(summary = "Mapa de calor: manchas RED (220 m) y AMBER (150 m)")
	public List<HeatSpot> heat() {
		return prevention.heat(currentUser.id());
	}

	@GetMapping("/api/v1/mitigation/summary")
	@Operation(summary = "Tengo / necesito por recurso de mitigación")
	public List<ResourceSummaryDto> mitigationSummary() {
		return prevention.mitigationSummary(currentUser.id());
	}

	@GetMapping("/api/v1/mitigation")
	@Operation(summary = "Listados de mitigación (sacos, motobombas, calaminas...)")
	public List<ResourceDto> mitigation(@RequestParam(required = false) MitigationType type,
			@RequestParam(required = false) ListingKind kind) {
		return prevention.mitigation(currentUser.id(), type, kind);
	}

	@PostMapping("/api/v1/mitigation")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Publicar Necesito / Ofrezco de mitigación")
	public ResourceDto createMitigation(@Valid @RequestBody MitigationRequest body) {
		return prevention.createMitigation(currentUser.id(), body);
	}
}
