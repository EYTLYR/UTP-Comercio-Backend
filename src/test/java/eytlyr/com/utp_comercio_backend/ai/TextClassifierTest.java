package eytlyr.com.utp_comercio_backend.ai;

import org.junit.jupiter.api.Test;

import eytlyr.com.utp_comercio_backend.zone.ZoneMode;

import static org.assertj.core.api.Assertions.assertThat;

/** Las frases de la demo del micrófono inteligente (ANEXO B). */
class TextClassifierTest {

	final TextClassifier classifier = new TextClassifier();

	@Test
	void emergencyPhrase() {
		var r = classifier.classify("Por favor ayúdenme, se cayó la pared de mi casa en Los Pinos 340 y mi papá está "
				+ "herido de un brazo", ZoneMode.EMERGENCY);
		assertThat(r.type()).isEqualTo("VULNERABLE");
		assertThat(r.category()).isEqualTo("INJURED");
		assertThat(r.priority()).isEqualTo("HIGH");
		assertThat(r.location()).isEqualTo("Jr. Los Pinos 340");
	}

	@Test
	void preventionPhrase() {
		var r = classifier.classify("La acequia de Los Pinos 330 ya está al ras y está entrando agua por la vereda",
				ZoneMode.PREVENTION);
		assertThat(r.type()).isEqualTo("RISK_REPORT");
		assertThat(r.category()).isEqualTo("ACEQUIA_OVERFLOW");
		assertThat(r.priority()).isEqualTo("HIGH");
		assertThat(r.location()).isEqualTo("Jr. Los Pinos 330");
	}

	@Test
	void dailyAndResourcePhrases() {
		var normal = classifier.classify("Presto mi escalera hasta el domingo", ZoneMode.NORMAL);
		assertThat(normal.type()).isEqualTo("OFFER");
		assertThat(normal.category()).isEqualTo("TOOL_LOAN");
		var water = classifier.classify("Tengo agua para compartir en la manzana C lote 4", ZoneMode.EMERGENCY);
		assertThat(water.type()).isEqualTo("RESOURCE");
		assertThat(water.kind()).isEqualTo("OFFER");
		assertThat(water.location()).isEqualTo("Mz. C lote 4");
	}
}
