package eytlyr.com.utp_comercio_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;
import eytlyr.com.utp_comercio_backend.ai.GeminiTextClassifier;
import eytlyr.com.utp_comercio_backend.ai.TextClassifier;
import eytlyr.com.utp_comercio_backend.prevention.GeminiPhotoRiskAnalyzer;
import eytlyr.com.utp_comercio_backend.prevention.PhotoRiskAnalyzer;
import eytlyr.com.utp_comercio_backend.sms.HttpSmsGateway;
import eytlyr.com.utp_comercio_backend.sms.SmsGateway;

import static org.assertj.core.api.Assertions.assertThat;

/** Con provider=gemini y sms.provider=http la app arranca con las implementaciones reales. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"nearby.ai.provider=gemini",
		"nearby.ai.gemini.api-key=clave-de-prueba",
		"nearby.sms.provider=http",
		"nearby.sms.gateway.url=http://127.0.0.1:1",
		"nearby.sms.gateway.api-key=clave-de-prueba" })
class RealProvidersWiringTest {

	@Autowired
	TextClassifier classifier;

	@Autowired
	PhotoRiskAnalyzer photos;

	@Autowired
	SmsGateway sms;

	@Test
	void realImplementationsAreSelected() {
		assertThat(classifier).isInstanceOf(GeminiTextClassifier.class);
		assertThat(photos).isInstanceOf(GeminiPhotoRiskAnalyzer.class);
		assertThat(sms).isInstanceOf(HttpSmsGateway.class);
	}
}
