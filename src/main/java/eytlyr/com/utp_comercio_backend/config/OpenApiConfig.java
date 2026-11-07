package eytlyr.com.utp_comercio_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	private static final String BEARER = "bearerAuth";

	@Bean
	OpenAPI nearbyOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Nearby API")
						.version("v1")
						.description("Servicios de proximidad con Visto Bueno Vecinal, cadena de favores, "
								+ "modo prevención (El Niño) y modo emergencia. San Juan de Lurigancho, Lima."))
				.components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}
}
