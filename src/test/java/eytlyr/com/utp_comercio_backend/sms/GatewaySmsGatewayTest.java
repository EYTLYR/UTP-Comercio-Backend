package eytlyr.com.utp_comercio_backend.sms;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eytlyr.com.utp_comercio_backend.config.NearbyProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El backend consume el microservicio SMS: POST /v1/messages con X-Api-Key. */
class GatewaySmsGatewayTest {

	HttpServer fake;

	final AtomicReference<String> body = new AtomicReference<>();

	final AtomicReference<String> key = new AtomicReference<>();

	@BeforeEach
	void start() throws Exception {
		fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		fake.createContext("/v1/messages", ex -> {
			key.set(ex.getRequestHeaders().getFirst("X-Api-Key"));
			body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			boolean ok = "clave-secreta".equals(key.get());
			byte[] out = (ok ? "{\"id\":\"abc\",\"state\":\"Pending\"}" : "{\"error\":\"X-Api-Key inválida\"}")
					.getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(ok ? 202 : 401, out.length);
			ex.getResponseBody().write(out);
			ex.close();
		});
		fake.start();
	}

	@AfterEach
	void stop() {
		fake.stop(0);
	}

	String url() {
		return "http://127.0.0.1:" + fake.getAddress().getPort() + "/";
	}

	@Test
	void sendsToMicroserviceWithApiKey() {
		new GatewaySmsGateway(new NearbyProperties.Sms.Gateway(url(), "clave-secreta"))
				.send("987654321", "Tu código Nearby es 123456. No lo compartas.");
		assertThat(key.get()).isEqualTo("clave-secreta");
		assertThat(body.get()).contains("\"to\":\"987654321\"").contains("Tu código Nearby es 123456");
	}

	@Test
	void rejectedSendFailsWithServiceMessage() {
		var gateway = new GatewaySmsGateway(new NearbyProperties.Sms.Gateway(url(), "otra-clave"));
		assertThatThrownBy(() -> gateway.send("987654321", "hola"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("401")
				.hasMessageContaining("X-Api-Key inválida");
	}
}
