package eytlyr.com.utp_comercio_backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj inyectable para poder fijar la fecha en las pruebas. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	public static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Bean
	Clock clock() {
		return Clock.system(LIMA);
	}
}
