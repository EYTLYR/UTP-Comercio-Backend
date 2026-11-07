package eytlyr.com.utp_comercio_backend;

import org.springframework.boot.SpringApplication;

/**
 * Arranca la app con un PostgreSQL desechable (Testcontainers o embebido, ver TestcontainersConfiguration).
 * Uso: ./mvnw spring-boot:test-run. No necesita Docker ni una base de datos instalada.
 */
public class TestUtpComercioBackendApplication {

	public static void main(String[] args) {
		// La base de datos la pone TestcontainersConfiguration; compose.yaml no se usa aquí.
		System.setProperty("spring.docker.compose.enabled", "false");
		SpringApplication.from(UtpComercioBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
