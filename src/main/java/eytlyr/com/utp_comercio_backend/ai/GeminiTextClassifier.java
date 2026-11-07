package eytlyr.com.utp_comercio_backend.ai;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import eytlyr.com.utp_comercio_backend.ai.dto.AiDtos.ClassifyResult;
import eytlyr.com.utp_comercio_backend.common.TextUtils;
import eytlyr.com.utp_comercio_backend.emergency.ResourceType;
import eytlyr.com.utp_comercio_backend.emergency.VulnerableCategory;
import eytlyr.com.utp_comercio_backend.posts.PostCategory;
import eytlyr.com.utp_comercio_backend.prevention.MitigationType;
import eytlyr.com.utp_comercio_backend.prevention.RiskCategory;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Micrófono inteligente con Gemini (nearby.ai.provider=gemini): convierte lo que dicta el vecino en el
 * formulario (tipo, categoría, prioridad y ubicación) en JSON. Solo acepta valores que la app conoce para
 * el modo de la zona; si Gemini falla, tarda, el circuito está abierto o devuelve algo fuera de lista,
 * responde el clasificador de reglas ({@link TextClassifier}), así que nunca hay un 500.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "nearby.ai", name = "provider", havingValue = "gemini")
public class GeminiTextClassifier extends TextClassifier {

	private static final Logger log = LoggerFactory.getLogger(GeminiTextClassifier.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final GeminiClient gemini;

	public GeminiTextClassifier(GeminiClient gemini) {
		this.gemini = gemini;
	}

	@Override
	public ClassifyResult classify(String text, ZoneMode mode) {
		try {
			return parse(gemini.generateJson(prompt(mode), text, null), text, mode);
		}
		catch (RuntimeException ex) {
			log.warn("Gemini no clasificó el texto ({}); se usan las reglas.", ex.getMessage());
			return super.classify(text, mode);
		}
	}

	static String prompt(ZoneMode mode) {
		String options = switch (mode) {
			case NORMAL -> """
					type: "NEED" (pide ayuda) u "OFFER" (ofrece algo). kind: null.
					category (favores entre vecinos):
					%s""".formatted(list(PostCategory.values()));
			case EMERGENCY -> """
					type: "VULNERABLE" (una persona necesita ayuda; kind: null) o "RESOURCE" (agua, comida, etc.;
					kind: "NEED" u "OFFER").
					category si type=VULNERABLE (o null si no se sabe):
					%s
					category si type=RESOURCE:
					%s""".formatted(list(VulnerableCategory.values()), list(ResourceType.values()));
			case PREVENTION -> """
					type: "RISK_REPORT" (avisa un riesgo; kind: null) o "MITIGATION" (materiales para prevenir;
					kind: "NEED" u "OFFER").
					category si type=RISK_REPORT (o null si no se sabe):
					%s
					category si type=MITIGATION:
					%s""".formatted(list(RiskCategory.values()), list(MitigationType.values()));
		};
		return """
				Eres el micrófono inteligente de una app vecinal de Lima (San Juan de Lurigancho). Un vecino dictó
				por voz lo que necesita u ofrece. Conviértelo en un formulario usando SOLO estos valores:
				%s
				priority: "HIGH" si hay riesgo para la vida o algo urgente, si no "MEDIUM".
				location: la dirección mencionada tal como se escribiría ("Jr. Los Pinos 340", "Mz. C lote 12")
				o null si no menciona ninguna.
				Responde SOLO con JSON:
				{"type":"...","kind":"..." o null,"category":"..." o null,"priority":"HIGH|MEDIUM","location":"..." o null}
				""".formatted(options);
	}

	private static String list(Enum<?>[] values) {
		return Arrays.stream(values).map(v -> "- " + v.name() + ": " + label(v)).collect(Collectors.joining("\n"));
	}

	/** Valida el JSON contra los valores permitidos del modo y arma el mismo resultado que las reglas. */
	static ClassifyResult parse(String json, String text, ZoneMode mode) {
		JsonNode n = JSON.readTree(json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1));
		String type = upper(n, "type");
		String kind = upper(n, "kind");
		String categoryName = upper(n, "category");
		String priority = "HIGH".equals(upper(n, "priority")) ? "HIGH" : "MEDIUM";
		String location = n.path("location").isString() && !n.path("location").asText().isBlank()
				? n.path("location").asText().trim()
				: location(TextUtils.normalize(text));

		Enum<?> category = switch (mode) {
			case NORMAL -> {
				require(type, "NEED", "OFFER");
				kind = null;
				yield require(PostCategory.class, categoryName, false);
			}
			case EMERGENCY -> switch (type == null ? "" : type) {
				case "VULNERABLE" -> {
					kind = null;
					yield require(VulnerableCategory.class, categoryName, true);
				}
				case "RESOURCE" -> {
					require(kind, "NEED", "OFFER");
					yield require(ResourceType.class, categoryName, false);
				}
				default -> throw new IllegalArgumentException("type no válido para EMERGENCY: " + type);
			};
			case PREVENTION -> switch (type == null ? "" : type) {
				case "RISK_REPORT" -> {
					kind = null;
					yield require(RiskCategory.class, categoryName, true);
				}
				case "MITIGATION" -> {
					require(kind, "NEED", "OFFER");
					yield require(MitigationType.class, categoryName, false);
				}
				default -> throw new IllegalArgumentException("type no válido para PREVENTION: " + type);
			};
		};
		return result(type, kind, category != null ? category.name() : null,
				category != null ? label(category) : null, priority, location);
	}

	private static String upper(JsonNode n, String field) {
		JsonNode v = n.path(field);
		return v.isString() && !v.asText().isBlank() ? v.asText().trim().toUpperCase() : null;
	}

	private static void require(String value, String... allowed) {
		if (value == null || Arrays.stream(allowed).noneMatch(value::equals)) {
			throw new IllegalArgumentException("Valor no válido: " + value);
		}
	}

	private static <E extends Enum<E>> E require(Class<E> type, String name, boolean nullable) {
		if (name == null) {
			if (nullable) {
				return null;
			}
			throw new IllegalArgumentException("Falta la categoría " + type.getSimpleName());
		}
		return Enum.valueOf(type, name);
	}

	private static String label(Enum<?> e) {
		return switch (e) {
			case PostCategory c -> c.label();
			case VulnerableCategory c -> c.label();
			case ResourceType c -> c.label();
			case RiskCategory c -> c.label();
			case MitigationType c -> c.label();
			default -> e.name();
		};
	}
}
