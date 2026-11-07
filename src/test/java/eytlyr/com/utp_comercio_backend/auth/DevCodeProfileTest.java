package eytlyr.com.utp_comercio_backend.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El código SMS fijo solo vale con dev o local, y nunca con prod. */
class DevCodeProfileTest {

	@Test
	void fixedCodeOnlyWithDevOrLocalAndNeverWithProd() {
		assertThat(AuthService.devCodeAllowed(new String[] { "dev" }, "123456")).isTrue();
		assertThat(AuthService.devCodeAllowed(new String[] { "local", "dev" }, "123456")).isTrue();

		assertThat(AuthService.devCodeAllowed(new String[] { "prod" }, "123456")).isFalse();
		assertThat(AuthService.devCodeAllowed(new String[] { "dev", "prod" }, "123456")).isFalse();
		assertThat(AuthService.devCodeAllowed(new String[] {}, "123456")).isFalse();

		assertThat(AuthService.devCodeAllowed(new String[] { "dev" }, "")).isFalse();
		assertThat(AuthService.devCodeAllowed(new String[] { "dev" }, null)).isFalse();
	}
}
