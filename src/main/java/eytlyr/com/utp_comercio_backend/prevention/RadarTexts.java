package eytlyr.com.utp_comercio_backend.prevention;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import eytlyr.com.utp_comercio_backend.ai.CapabilityTag;

/** Reglas y textos del radar preventivo (ANEXO B, sección prevención). */
public final class RadarTexts {

	private RadarTexts() {
	}

	static final Map<RiskCategory, Double> WEIGHTS = new EnumMap<>(Map.of(
			RiskCategory.RAVINE_ACTIVITY, 2.0, RiskCategory.CRACK, 2.0, RiskCategory.ACEQUIA_OVERFLOW, 1.5,
			RiskCategory.STREET_FLOODING, 1.0, RiskCategory.SEEPAGE, 1.0, RiskCategory.DRAIN_OVERFLOW, 1.0));

	static final Map<RiskCategory, List<CapabilityTag>> MITIGATION_NEEDS = new EnumMap<>(Map.of(
			RiskCategory.ACEQUIA_OVERFLOW, List.of(CapabilityTag.SANDBAGS, CapabilityTag.PUMP, CapabilityTag.SHOVELS),
			RiskCategory.STREET_FLOODING, List.of(CapabilityTag.SANDBAGS, CapabilityTag.PUMP),
			RiskCategory.SEEPAGE, List.of(CapabilityTag.SANDBAGS, CapabilityTag.ROOF_COVER),
			RiskCategory.RAVINE_ACTIVITY, List.of(CapabilityTag.SANDBAGS, CapabilityTag.EVAC_TRANSPORT),
			RiskCategory.DRAIN_OVERFLOW, List.of(CapabilityTag.SHOVELS, CapabilityTag.PUMP),
			RiskCategory.CRACK, List.of(CapabilityTag.SANDBAGS, CapabilityTag.EVAC_TRANSPORT)));

	public static String shortName(RiskCategory c) {
		return switch (c) {
			case ACEQUIA_OVERFLOW -> "acequia al ras";
			case STREET_FLOODING -> "agua acumulada";
			case SEEPAGE -> "filtración";
			case RAVINE_ACTIVITY -> "quebrada activa";
			case DRAIN_OVERFLOW -> "desagüe colapsado";
			case CRACK -> "grieta";
		};
	}

	/** Inferencia en el orden del ANEXO B. */
	static String inference(Collection<RiskCategory> cats) {
		if (cats.contains(RiskCategory.RAVINE_ACTIVITY)) {
			return "posible activación de la quebrada (huaico): prepara la evacuación";
		}
		if (cats.contains(RiskCategory.ACEQUIA_OVERFLOW)
				&& (cats.contains(RiskCategory.SEEPAGE) || cats.contains(RiskCategory.STREET_FLOODING))) {
			return "la acequia podría desbordarse y anegar la calle";
		}
		if (cats.contains(RiskCategory.ACEQUIA_OVERFLOW)) {
			return "la acequia está cerca de desbordarse";
		}
		if (cats.contains(RiskCategory.SEEPAGE)) {
			return "hay filtración subterránea: revisa cimientos y muros";
		}
		return "riesgo de aniego en la pista y las viviendas bajas";
	}

	static String riskName(Collection<RiskCategory> cats) {
		if (cats.contains(RiskCategory.RAVINE_ACTIVITY)) {
			return "huaico";
		}
		if (cats.contains(RiskCategory.ACEQUIA_OVERFLOW)) {
			return "desborde de acequia";
		}
		return "aniegos";
	}

	/** "3 vecinos de Jr. Los Pinos reportaron filtración y acequia al ras en la última hora: la acequia podría desbordarse y anegar la calle." */
	static String pattern(int reports, String street, List<RiskCategory> orderedDistinct) {
		return reports + " vecinos de " + street + " reportaron "
				+ joinSpanish(orderedDistinct.stream().map(RadarTexts::shortName).toList()) + " en la última hora: "
				+ inference(orderedDistinct) + ".";
	}

	/**
	 * "Se detectó riesgo de desborde de acequia en Jr. Los Pinos. Familia Ríos tiene sacos de arena y motobomba
	 * a 156 m. ¿Coordinamos el préstamo preventivo?"
	 */
	static String alert(String riskName, String street, String neighbor, List<CapabilityTag> tags, Integer distanceM,
			boolean ravine) {
		StringBuilder sb = new StringBuilder("Se detectó riesgo de ").append(riskName).append(" en ").append(street).append('.');
		if (neighbor != null && !tags.isEmpty()) {
			sb.append(' ').append(neighbor).append(" tiene ")
					.append(joinSpanish(tags.stream().map(t -> t.label().toLowerCase()).toList()))
					.append(" a ").append(distanceM).append(" m.");
			boolean transportOnly = tags.stream().allMatch(t -> t == CapabilityTag.EVAC_TRANSPORT);
			sb.append(transportOnly ? " ¿Coordinamos la evacuación preventiva de adultos mayores y niños?"
					: " ¿Coordinamos el préstamo preventivo?");
		}
		else {
			sb.append(" Revisa la ruta al punto seguro.");
		}
		if (ravine) {
			sb.append(" Si baja lodo o piedras, llama a Defensa Civil (115).");
		}
		return sb.toString();
	}

	/** ["a", "b", "c"] → "a, b y c". */
	public static String joinSpanish(List<String> items) {
		if (items.isEmpty()) {
			return "";
		}
		if (items.size() == 1) {
			return items.getFirst();
		}
		return String.join(", ", items.subList(0, items.size() - 1)) + " y " + items.getLast();
	}
}
