package eytlyr.com.utp_comercio_backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API sin estado con JWT HS256. Los tokens los emite el módulo auth (FASE 3).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

	static final String[] PUBLIC_PATHS = {
			"/api/v1/auth/**",
			"/actuator/health", "/actuator/health/**",
			"/v3/api-docs", "/v3/api-docs/**",
			"/swagger-ui.html", "/swagger-ui/**",
			"/error"
	};

	private static final AuthenticationEntryPoint UNAUTHORIZED = (req, res, ex) -> writeProblem(res, 401,
			"No autenticado", "Inicia sesión para continuar.", "UNAUTHORIZED");

	private static final AccessDeniedHandler FORBIDDEN = (req, res, ex) -> writeProblem(res, 403,
			"Acceso denegado", "No tienes permiso para esta acción.", "FORBIDDEN");

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> { })
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(rs -> rs
						.jwt(jwt -> { })
						.authenticationEntryPoint(UNAUTHORIZED)
						.accessDeniedHandler(FORBIDDEN))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(UNAUTHORIZED)
						.accessDeniedHandler(FORBIDDEN));
		return http.build();
	}

	@Bean
	SecretKey jwtSecretKey(NearbyProperties props) {
		return new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
		return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(NearbyProperties props) {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		List<String> origins = props.cors().allowedOriginPatterns().stream().filter(o -> !o.isBlank()).toList();
		if (!origins.isEmpty()) {
			CorsConfiguration cfg = new CorsConfiguration();
			cfg.setAllowedOriginPatterns(origins);
			cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
			cfg.setAllowedHeaders(List.of("*"));
			cfg.setExposedHeaders(List.of("Location"));
			cfg.setMaxAge(3600L);
			source.registerCorsConfiguration("/**", cfg);
		}
		return source;
	}

	/**
	 * ProblemDetail (RFC 9457) escrito a mano porque el filtro de seguridad corre antes de Spring MVC.
	 * Los textos son constantes de esta clase, así que no hace falta escaparlos.
	 */
	private static void writeProblem(HttpServletResponse res, int status, String title, String detail, String code)
			throws IOException {
		res.setStatus(status);
		res.setCharacterEncoding(StandardCharsets.UTF_8.name());
		res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
				+ ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
	}
}
