package eytlyr.com.utp_comercio_backend.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.ClassifyResult;
import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.emergency.ResourceType;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.posts.PostCategory;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;
import eytlyr.com.utp_comercio_backend.prevention.RiskCategory;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;

/**
 * Micrófono inteligente (ANEXO B, sección classify): convierte lo que dicta el vecino en un formulario.
 * El teléfono hace lo mismo sin internet; este endpoint sirve para validar y para la versión web.
 */
@Component
public class TextClassifier {

	private static final Pattern STREET = Pattern.compile("(los pinos|las flores|las rosas|principal)\\s*(\\d{1,4})");

	private static final Pattern BLOCK = Pattern.compile("(?:manzana|mz\\.?)\\s*([a-z])\\s*lote\\s*(\\d+)");

	private static final Map<String, String> STREET_NAMES = Map.of("los pinos", "Jr. Los Pinos", "las flores",
			"Jr. Las Flores", "las rosas", "Pje. Las Rosas", "principal", "Av. Principal");

	private static final Map<VulnerableCategory, List<String>> VULNERABLE = new LinkedHashMap<>();

	private static final Map<ResourceType, List<String>> RESOURCES = new LinkedHashMap<>();

	private static final Map<PostCategory, List<String>> POSTS = new LinkedHashMap<>();

	private static final Map<MitigationType, List<String>> MITIGATION = new LinkedHashMap<>();

	private static final Map<RiskCategory, List<String>> RISKS = new LinkedHashMap<>();

	static {
		VULNERABLE.put(VulnerableCategory.INJURED, List.of("herid", "sangr", "fractur", "golpe"));
		VULNERABLE.put(VulnerableCategory.TRAPPED, List.of("atrapad", "se cayo", "escombro", "derrumb"));
		VULNERABLE.put(VulnerableCategory.OXYGEN_DEPENDENT, List.of("oxigeno"));
		VULNERABLE.put(VulnerableCategory.ELDERLY_ALONE, List.of("abuel", "adulto mayor", "anciano"));
		VULNERABLE.put(VulnerableCategory.CHILDREN_ALONE, List.of("nino", "nina", "bebe"));
		VULNERABLE.put(VulnerableCategory.PREGNANT, List.of("embarazada", "gestante"));
		VULNERABLE.put(VulnerableCategory.DISABILITY, List.of("silla de ruedas", "discapacidad"));

		RESOURCES.put(ResourceType.WATER, List.of("agua"));
		RESOURCES.put(ResourceType.FOOD, List.of("comida", "viveres"));
		RESOURCES.put(ResourceType.MEDICINE, List.of("medicina"));
		RESOURCES.put(ResourceType.ENERGY, List.of("carga"));
		RESOURCES.put(ResourceType.SHELTER, List.of("refugio"));
		RESOURCES.put(ResourceType.TRANSPORT, List.of("transporte"));

		POSTS.put(PostCategory.TOOL_LOAN, List.of("escalera", "taladro", "herramienta"));
		POSTS.put(PostCategory.TEMPORARY_HELP, List.of("acompan", "cuidar"));
		POSTS.put(PostCategory.MATERIAL_DONATION, List.of("ladrillo", "cemento", "madera", "material"));
		POSTS.put(PostCategory.SKILL_EXCHANGE, List.of("ensen", "clase", "tarea"));
		POSTS.put(PostCategory.SOLIDARITY_RIDE, List.of("llevar", "lleven", "auto", "asiento", "mercado", "cita"));

		MITIGATION.put(MitigationType.SANDBAGS, List.of("sacos"));
		MITIGATION.put(MitigationType.PUMP, List.of("motobomba"));
		MITIGATION.put(MitigationType.ROOF_COVER, List.of("calamina", "plastico"));
		MITIGATION.put(MitigationType.SHOVELS, List.of("palas"));
		MITIGATION.put(MitigationType.PLANKS, List.of("tablones"));

		RISKS.put(RiskCategory.RAVINE_ACTIVITY, List.of("quebrada", "huaico", "lodo", "piedras"));
		RISKS.put(RiskCategory.CRACK, List.of("grieta", "rajadura"));
		RISKS.put(RiskCategory.ACEQUIA_OVERFLOW, List.of("acequia", "canal"));
		RISKS.put(RiskCategory.SEEPAGE, List.of("filtra", "humedad", "gotea", "brota"));
		RISKS.put(RiskCategory.DRAIN_OVERFLOW, List.of("desague", "buzon"));
		RISKS.put(RiskCategory.STREET_FLOODING, List.of("agua", "charco", "inund", "aniego", "laguna"));
	}

	private static final List<String> EMERGENCY_HIGH = List.of("herid", "sangr", "atrapad", "no respira", "inconsciente",
			"fuego", "incendio", "se cayo", "derrumb");

	private static final List<String> PREVENTION_HIGH = List.of("desborda", "huaico", "grieta", "entra agua", "al ras",
			"sube rapido");

	private static final List<String> OFFER_WORDS = List.of("presto", "ofrezco", "regalo", "tengo", "enseno", "puedo",
			"dono", "comparto");

	public ClassifyResult classify(String text, ZoneMode mode) {
		String t = TextUtils.normalize(text);
		String location = location(t);
		return switch (mode) {
			case EMERGENCY -> emergency(t, location);
			case PREVENTION -> prevention(t, location);
			case NORMAL -> normal(t, location);
		};
	}

	private ClassifyResult emergency(String t, String location) {
		boolean person = containsAny(t, List.of("herid", "atrapad", "sangr"));
		ResourceType resource = person ? null : first(RESOURCES, t);
		if (resource != null) {
			String kind = offer(t) ? "OFFER" : "NEED";
			return result("RESOURCE", kind, resource.name(), resource.label(), "MEDIUM", location);
		}
		VulnerableCategory category = first(VULNERABLE, t);
		String priority = containsAny(t, EMERGENCY_HIGH) ? "HIGH" : "MEDIUM";
		return result("VULNERABLE", null, category != null ? category.name() : null,
				category != null ? category.label() : null, priority, location);
	}

	private ClassifyResult prevention(String t, String location) {
		MitigationType mitigation = first(MITIGATION, t);
		if (mitigation != null) {
			return result("MITIGATION", offer(t) ? "OFFER" : "NEED", mitigation.name(), mitigation.label(), "MEDIUM",
					location);
		}
		RiskCategory risk = first(RISKS, t);
		String priority = containsAny(t, PREVENTION_HIGH) ? "HIGH" : "MEDIUM";
		return result("RISK_REPORT", null, risk != null ? risk.name() : null, risk != null ? risk.label() : null,
				priority, location);
	}

	private ClassifyResult normal(String t, String location) {
		String type = offer(t) ? "OFFER" : "NEED";
		PostCategory category = first(POSTS, t);
		if (category == null) {
			category = PostCategory.TEMPORARY_HELP;
		}
		return result(type, null, category.name(), category.label(), "MEDIUM", location);
	}

	private static ClassifyResult result(String type, String kind, String category, String label, String priority,
			String location) {
		List<String> chips = new ArrayList<>();
		if (label != null) {
			chips.add(label);
		}
		if ("HIGH".equals(priority)) {
			chips.add("Prioridad alta");
		}
		if (location != null) {
			chips.add(location);
		}
		return new ClassifyResult(type, kind, category, label, priority, location, chips);
	}

	/** "los pinos 340" → "Jr. Los Pinos 340"; "manzana c lote 12" → "Mz. C lote 12". */
	static String location(String normalized) {
		Matcher m = STREET.matcher(normalized);
		if (m.find()) {
			return STREET_NAMES.get(m.group(1)) + " " + m.group(2);
		}
		Matcher b = BLOCK.matcher(normalized);
		if (b.find()) {
			return "Mz. " + b.group(1).toUpperCase() + " lote " + b.group(2);
		}
		return null;
	}

	private static boolean offer(String t) {
		return containsAny(t, OFFER_WORDS);
	}

	private static boolean containsAny(String t, List<String> words) {
		return words.stream().anyMatch(t::contains);
	}

	private static <K> K first(Map<K, List<String>> dictionary, String t) {
		return dictionary.entrySet().stream()
				.filter(e -> containsAny(t, e.getValue()))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse(null);
	}
}
