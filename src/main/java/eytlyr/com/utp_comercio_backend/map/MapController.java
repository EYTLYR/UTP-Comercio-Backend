package eytlyr.com.utp_comercio_backend.map;

import java.util.List;
import java.util.Locale;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.auth.CurrentUser;
import eytlyr.com.utp_comercio_backend.common.BusinessRuleException;
import eytlyr.com.utp_comercio_backend.map.MapService.Layer;
import eytlyr.com.utp_comercio_backend.map.MapService.Pin;

@RestController
@Tag(name = "Mapa")
public class MapController {

	private final MapService map;

	private final CurrentUser currentUser;

	public MapController(MapService map, CurrentUser currentUser) {
		this.map = map;
		this.currentUser = currentUser;
	}

	@GetMapping("/api/v1/map/pins")
	@Operation(summary = "Pines del mapa: layer=daily|emergency|prevention")
	public List<Pin> pins(@RequestParam(defaultValue = "daily") String layer,
			@RequestParam(defaultValue = "5000") int radiusM,
			@RequestParam(required = false) java.util.UUID zoneId) {
		Layer l;
		try {
			l = Layer.valueOf(layer.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessRuleException("MAP_LAYER_INVALID", "La capa debe ser daily, emergency o prevention.");
		}
		// zoneId: explorar otra zona (pines alrededor de su centro).
		return zoneId != null ? map.pinsInZone(currentUser.id(), zoneId, l, radiusM)
				: map.pins(currentUser.id(), l, radiusM);
	}
}
