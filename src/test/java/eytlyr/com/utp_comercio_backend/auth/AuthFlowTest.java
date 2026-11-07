package eytlyr.com.utp_comercio_backend.auth;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import eytlyr.com.utp_comercio_backend.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FASE 3: ingreso con celular + código SMS, JWT y token de refresco, contra PostgreSQL real. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

	static final String MARIA = "987654321";

	static final String BRIGADA = "999000111";

	static final String DEV_CODE = "123456";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	JwtAuthenticationConverter jwtConverter;

	@BeforeEach
	void cleanOtps() {
		jdbc.update("delete from otp_code");
	}

	@AfterEach
	void removeTestUsers() {
		jdbc.update("delete from app_user where phone like '9000000%'");
	}

	@Test
	void happyPathRequestVerifyAndMe() throws Exception {
		requestOtp(MARIA)
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.expiresInSeconds").value(300))
				.andExpect(jsonPath("$.resendInSeconds").value(30))
				.andExpect(jsonPath("$.devCode").value(DEV_CODE));

		String body = verify(MARIA, DEV_CODE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isNewUser").value(false))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.user.name").value("María Quispe"))
				.andExpect(jsonPath("$.user.zone.name").value("Zona 3 · Las Flores"))
				.andExpect(jsonPath("$.user.effectiveMode").value("NORMAL"))
				.andReturn().getResponse().getContentAsString();

		mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + JsonPath.read(body, "$.accessToken")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phone").value(MARIA))
				.andExpect(jsonPath("$.role").value("NEIGHBOR"))
				.andExpect(jsonPath("$.aiConsent").value(true));
	}

	@Test
	void accessTokenCarriesRoleAndZone() throws Exception {
		Jwt jwt = jwtDecoder.decode(login(BRIGADA).accessToken());
		assertThat(jwt.getClaimAsString("role")).isEqualTo("BRIGADIST");
		assertThat(jwt.getClaimAsString("phone")).isEqualTo(BRIGADA);
		assertThat(jwt.getClaimAsString("zoneId")).isNotBlank();
		assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtService.ISSUER);
		assertThat(jwtConverter.convert(jwt).getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.contains("ROLE_BRIGADIST") // Spring Security 7 agrega además FACTOR_BEARER
				.noneMatch(a -> a.startsWith("ROLE_") && !a.equals("ROLE_BRIGADIST"));
	}

	@Test
	void otpIsStoredHashed() throws Exception {
		requestOtp(MARIA).andExpect(status().isAccepted());
		String hash = jdbc.queryForObject("select code_hash from otp_code where phone = ?", String.class, MARIA);
		assertThat(hash).hasSize(64).doesNotContain(DEV_CODE);
	}

	@Test
	void wrongCodeIs401() throws Exception {
		requestOtp(MARIA).andExpect(status().isAccepted());
		verify(MARIA, "000000")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(AuthService.OTP_INVALID))
				.andExpect(jsonPath("$.detail").value("Código incorrecto. Revisa el SMS e inténtalo otra vez"));
	}

	@Test
	void codeIsSingleUse() throws Exception {
		requestOtp(MARIA).andExpect(status().isAccepted());
		verify(MARIA, DEV_CODE).andExpect(status().isOk());
		verify(MARIA, DEV_CODE).andExpect(status().isUnauthorized());
	}

	@Test
	void expiredCodeIs401() throws Exception {
		requestOtp("900000052").andExpect(status().isAccepted());
		jdbc.update("update otp_code set expires_at = now() - interval '1 minute' where phone = ?", "900000052");
		verify("900000052", DEV_CODE)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(AuthService.OTP_INVALID));
	}

	@Test
	void invalidPhoneIs400() throws Exception {
		requestOtp("12345")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.detail").value("Los celulares en Perú tienen 9 dígitos y empiezan con 9"))
				.andExpect(jsonPath("$.errors[0].field").value("phone"));
	}

	@Test
	void tooManyRequestsIs429() throws Exception {
		for (int i = 0; i < 5; i++) {
			requestOtp("900000050").andExpect(status().isAccepted());
		}
		requestOtp("900000050")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value(AuthService.OTP_TOO_MANY_REQUESTS))
				.andExpect(jsonPath("$.detail").value("Demasiados intentos, espera unos minutos"));
	}

	@Test
	void tooManyAttemptsIs429AndBurnsTheCode() throws Exception {
		requestOtp("900000051").andExpect(status().isAccepted());
		for (int i = 0; i < 5; i++) {
			verify("900000051", "000000").andExpect(status().isUnauthorized());
		}
		verify("900000051", "000000")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value(AuthService.OTP_TOO_MANY_ATTEMPTS));
		verify("900000051", DEV_CODE).andExpect(status().isUnauthorized());
	}

	@Test
	void newPhoneCreatesANeighbor() throws Exception {
		requestOtp("900000053").andExpect(status().isAccepted());
		verify("900000053", DEV_CODE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isNewUser").value(true))
				.andExpect(jsonPath("$.user.role").value("NEIGHBOR"))
				.andExpect(jsonPath("$.user.verified").value(false))
				.andExpect(jsonPath("$.user.zone").doesNotExist());
		assertThat(jdbc.queryForObject("select count(*) from app_user where phone = ?", Integer.class, "900000053"))
				.isEqualTo(1);
	}

	@Test
	void refreshTokenRotatesAndReuseClosesAllSessions() throws Exception {
		Tokens first = login(MARIA);

		String body = refresh(first.refreshToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isNewUser").value(false))
				.andReturn().getResponse().getContentAsString();
		String second = JsonPath.read(body, "$.refreshToken");
		assertThat(second).isNotEqualTo(first.refreshToken());

		// Reusar el token ya rotado: 401 y además se revoca el nuevo.
		refresh(first.refreshToken())
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(AuthService.REFRESH_INVALID));
		refresh(second).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesRefreshTokens() throws Exception {
		Tokens tokens = login(MARIA);
		mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + tokens.accessToken()))
				.andExpect(status().isNoContent());
		refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------- utilidades

	record Tokens(String accessToken, String refreshToken) {
	}

	Tokens login(String phone) throws Exception {
		requestOtp(phone).andExpect(status().isAccepted());
		String body = verify(phone, DEV_CODE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
	}

	ResultActions requestOtp(String phone) throws Exception {
		return mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\"}"));
	}

	ResultActions verify(String phone, String code) throws Exception {
		return mvc.perform(post("/api/v1/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"));
	}

	ResultActions refresh(String token) throws Exception {
		return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + token + "\"}"));
	}
}
