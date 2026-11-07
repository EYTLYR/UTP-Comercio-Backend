package eytlyr.com.utp_comercio_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UtpComercioBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(UtpComercioBackendApplication.class, args);
	}

}
