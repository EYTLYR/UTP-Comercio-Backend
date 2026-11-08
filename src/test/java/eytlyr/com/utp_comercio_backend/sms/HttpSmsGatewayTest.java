package eytlyr.com.utp_comercio_backend.sms;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntUnaryOperator;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eytlyr.com.utp_comercio_backend.config.NearbyProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El backend consume el microservicio SMS: POST /v1/messages con X-Api-Key, con 2 reintentos. */
class HttpSmsGatewayTest {

	HttpServer fake;

	final AtomicReference<String> body = new AtomicReference<>();

	final AtomicReference<String> key = new AtomicReference<>();

	final AtomicInteger hits = new AtomicInteger();

	/** Código HTTP que responde el microservicio falso según el número de intento (1, 2, 3...). */
	volatile IntUnaryOperator statusFor = attempt -> 202;

	@BeforeEach
	void start() throws Exception {
		fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		fake.createContext("/v1/messages", ex -> {
			int attempt = hits.incrementAndGet();
			key.set(ex.getRequestHeaders().getFirst("X-Api-Key"));
			body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			int status = "clave-secreta".equals(key.get()) ? statusFor.applyAsInt(attempt) : 401;
			String json = switch (status) {
				case 202 -> "{\"id\":\"abc\",\"state\":\"Pending\"}";
				case 401 -> "{\"error\":\"X-Api-Key inválida\"}";
				default -> "{\"error\":\"El celular no responde\"}";
			};
			byte[] out = json.getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(status, out.length);
			ex.getResponseBody().write(out);
			ex.close();
		});
		fake.start();
	}

	@AfterEach
	void stop() {
		fake.stop(0);
	}

	HttpSmsGateway gateway(String apiKey) {
		String url = "http://127.0.0.1:" + fake.getAddress().getPort() + "/";
		return new HttpSmsGateway(new NearbyProperties.Sms.Gateway(url, apiKey), Duration.ZERO);
	}

	@Test
	void sendsToMicroserviceWithApiKey() {
		gateway("clave-secreta").send("987654321", "Nearby: 123456 es tu clave para ingresar.");
		assertThat(hits.get()).isEqualTo(1);
		assertThat(key.get()).isEqualTo("clave-secreta");
		assertThat(body.get()).contains("\"to\":\"987654321\"").contains("Nearby: 123456 es tu clave para ingresar.");
	}

	@Test
	void retriesTwiceOnServerErrorsAndThenSucceeds() {
		statusFor = attempt -> attempt < 3 ? 503 : 202;
		gateway("clave-secreta").send("987654321", "NB#SOS!H1@LP340$P:ALTA");
		assertThat(hits.get()).isEqualTo(3);
	}

	@Test
	void givesUpAfterThreeAttempts() {
		statusFor = attempt -> 500;
		var gateway = gateway("clave-secreta");
		assertThatThrownBy(() -> gateway.send("987654321", "hola"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("500")
				.hasMessageContaining("El celular no responde");
		assertThat(hits.get()).isEqualTo(1 + HttpSmsGateway.RETRIES);
	}

	@Test
	void rejectedKeyIsNotRetried() {
		var gateway = gateway("otra-clave");
		assertThatThrownBy(() -> gateway.send("987654321", "hola"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("401")
				.hasMessageContaining("X-Api-Key inválida");
		assertThat(hits.get()).isEqualTo(1);
	}

	@Test
	void unreachableMicroserviceFailsAfterRetries() {
		// Puerto 1: nada escucha ahí, la conexión se rechaza.
		var gateway = new HttpSmsGateway(new NearbyProperties.Sms.Gateway("http://127.0.0.1:1", "clave-secreta"),
				Duration.ZERO);
		assertThatThrownBy(() -> gateway.send("987654321", "hola"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no respondió");
	}
}
