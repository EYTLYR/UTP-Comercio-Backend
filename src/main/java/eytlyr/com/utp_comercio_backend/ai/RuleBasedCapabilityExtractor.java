package eytlyr.com.utp_comercio_backend.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.common.TextUtils;

/**
 * Extractor por palabras clave (ANEXO B). Es el predeterminado y el respaldo del extractor con Anthropic.
 * La evidencia es la frase literal donde apareció la palabra clave.
 */
@Component("ruleBasedCapabilityExtractor")
public class RuleBasedCapabilityExtractor implements CapabilityExtractor {

	static final double KEYWORD_CONFIDENCE = 0.7;

	/** Palabras clave ya normalizadas (minúsculas, sin tildes). */
	static final Map<CapabilityTag, List<String>> KEYWORDS = new LinkedHashMap<>();

	static {
		KEYWORDS.put(CapabilityTag.HEIGHT_RESCUE, List.of("escalera"));
		KEYWORDS.put(CapabilityTag.RESCUE_TOOLS, List.of("palanca", "gata", "taladro", "barreta"));
		KEYWORDS.put(CapabilityTag.BACKUP_POWER, List.of("generador", "panel solar"));
		KEYWORDS.put(CapabilityTag.WATER_RESERVE, List.of("tanque de agua", "cisterna"));
		KEYWORDS.put(CapabilityTag.EVAC_TRANSPORT, List.of("mototaxi", "camioneta", "asientos libres"));
		KEYWORDS.put(CapabilityTag.PARAMEDIC, List.of("inyect", "enfermer", "primeros auxilios"));
		KEYWORDS.put(CapabilityTag.TECHNICIAN, List.of("electricista", "gasfiter"));
		KEYWORDS.put(CapabilityTag.ELDERLY_AT_HOME, List.of("abuela", "abuelo", "adulto mayor"));
		KEYWORDS.put(CapabilityTag.REDUCED_MOBILITY, List.of("silla de ruedas", "no puede caminar"));
		KEYWORDS.put(CapabilityTag.OXYGEN_DEPENDENT, List.of("oxigeno"));
		KEYWORDS.put(CapabilityTag.SANDBAGS, List.of("sacos de arena"));
		KEYWORDS.put(CapabilityTag.PUMP, List.of("motobomba"));
		KEYWORDS.put(CapabilityTag.ROOF_COVER, List.of("calamina", "plastico grueso"));
		KEYWORDS.put(CapabilityTag.SHOVELS, List.of("palas"));
	}

	@Override
	public List<ExtractedCapability> extract(String text, SourceType source) {
		List<ExtractedCapability> out = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return out;
		}
		String[] sentences = text.split("(?<=[.;!?])\\s+");
		for (var entry : KEYWORDS.entrySet()) {
			String evidence = null;
			for (String sentence : sentences) {
				String normalized = TextUtils.normalize(sentence);
				if (entry.getValue().stream().anyMatch(normalized::contains)) {
					evidence = sentence.trim();
					break;
				}
			}
			if (evidence != null) {
				out.add(new ExtractedCapability(entry.getKey(), KEYWORD_CONFIDENCE, truncate(evidence)));
			}
		}
		return out;
	}

	static String truncate(String s) {
		return s.length() <= 300 ? s : s.substring(0, 297) + "...";
	}
}
