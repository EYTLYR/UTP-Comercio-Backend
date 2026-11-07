package eytlyr.com.utp_comercio_backend.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eytlyr.com.utp_comercio_backend.config.NearbyProperties;
import eytlyr.com.utp_comercio_backend.config.SecurityConfig;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Errores ProblemDetail, seguridad (401/403) y CORS sin base de datos. */
@WebMvcTest(controllers = ApiErrorTest.ProbeController.class)
@Import({ SecurityConfig.class, ApiErrorTest.ProbeController.class })
@EnableConfigurationProperties(NearbyProperties.class)
class ApiErrorTest {

	@Autowired
	MockMvc mvc;

	@Test
	void withoutTokenReturns401Problem() throws Exception {
		mvc.perform(get("/api/v1/_probe/not-found"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.detail").value("Inicia sesión para continuar."));
	}

	@Test
	void invalidTokenReturns401() throws Exception {
		mvc.perform(get("/api/v1/_probe/not-found").header("Authorization", "Bearer no-es-un-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void notFoundException() throws Exception {
		mvc.perform(get("/api/v1/_probe/not-found").with(jwt()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("No se encontró el servicio."))
				.andExpect(jsonPath("$.title").value("No encontrado"));
	}

	@Test
	void businessRuleIs422() throws Exception {
		mvc.perform(get("/api/v1/_probe/rule").with(jwt()))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ENDORSEMENT_SELF"));
	}

	@Test
	void conflictIs409() throws Exception {
		mvc.perform(get("/api/v1/_probe/conflict").with(jwt()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ENDORSEMENT_DUPLICATE"));
	}

	@Test
	void validationListsFieldErrors() throws Exception {
		mvc.perform(post("/api/v1/_probe/validate").with(jwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ApiError.VALIDATION_ERROR))
				.andExpect(jsonPath("$.errors[0].field").value("title"))
				.andExpect(jsonPath("$.errors[0].message").value("El título es obligatorio"));
	}

	@Test
	void malformedJsonIs400WithCode() throws Exception {
		mvc.perform(post("/api/v1/_probe/validate").with(jwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{no es json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.title").value("Petición inválida"));
	}

	@Test
	void unexpectedErrorDoesNotLeakDetails() throws Exception {
		mvc.perform(get("/api/v1/_probe/boom").with(jwt()))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail", not(containsString("password=secreto"))));
	}

	@Test
	void roleCheckReturns403() throws Exception {
		mvc.perform(get("/api/v1/_probe/admin").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_NEIGHBOR"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(get("/api/v1/_probe/admin").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk());
	}

	@Test
	void authPathsArePublic() throws Exception {
		// No existe todavía (FASE 3), pero no debe pedir token: 404 en vez de 401.
		mvc.perform(get("/api/v1/auth/whatever"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("La ruta solicitada no existe."));
	}

	@Test
	void corsAllowsLocalhostInDev() throws Exception {
		mvc.perform(options("/api/v1/_probe/not-found")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
		mvc.perform(options("/api/v1/_probe/not-found")
						.header("Origin", "https://evil.example")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden());
	}

	record ProbeRequest(@NotBlank(message = "El título es obligatorio") @Size(max = 80) String title) {
	}

	@RestController
	@RequestMapping("/api/v1/_probe")
	static class ProbeController {

		@GetMapping("/not-found")
		String notFound() {
			throw new NotFoundException("SERVICE_NOT_FOUND", "No se encontró el servicio.");
		}

		@GetMapping("/rule")
		String rule() {
			throw new BusinessRuleException("ENDORSEMENT_SELF", "No puedes darte Visto Bueno a ti mismo.");
		}

		@GetMapping("/conflict")
		String conflict() {
			throw new ConflictException("ENDORSEMENT_DUPLICATE", "Ya diste Visto Bueno a este servicio.");
		}

		@PostMapping("/validate")
		String validate(@Valid @RequestBody ProbeRequest body) {
			return body.title();
		}

		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("password=secreto");
		}

		@GetMapping("/admin")
		@PreAuthorize("hasRole('ADMIN')")
		String admin() {
			return "ok";
		}
	}
}
