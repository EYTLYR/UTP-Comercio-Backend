package eytlyr.com.utp_comercio_backend;

import org.springframework.boot.SpringApplication;

public class TestUtpComercioBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(UtpComercioBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
