package eytlyr.com.utp_comercio_backend.user;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import eytlyr.com.utp_comercio_backend.user.dto.UserDto;
import eytlyr.com.utp_comercio_backend.zone.Zone;
import eytlyr.com.utp_comercio_backend.zone.ZoneMode;

import static org.assertj.core.api.Assertions.assertThat;

/** Los casos de effectiveMode de la FASE 4. */
class EffectiveModeTest {

	static final OffsetDateTime ACTIVATED = OffsetDateTime.parse("2026-11-06T10:00:00-05:00");

	@Test
	void localEmergencyWinsOverEverything() {
		AppUser u = user(zone(ZoneMode.NORMAL));
		u.setLocalEmergency(true);
		assertThat(UserDto.effectiveMode(u)).isEqualTo(ZoneMode.EMERGENCY);
	}

	@Test
	void zoneModeAppliesWhenNotDismissed() {
		assertThat(UserDto.effectiveMode(user(zone(ZoneMode.PREVENTION)))).isEqualTo(ZoneMode.PREVENTION);
	}

	@Test
	void dismissedAfterActivationShowsNormal() {
		AppUser u = user(zone(ZoneMode.EMERGENCY));
		u.setModeDismissedAt(ACTIVATED.plusMinutes(5));
		assertThat(UserDto.effectiveMode(u)).isEqualTo(ZoneMode.NORMAL);
	}

	@Test
	void dismissedBeforeANewActivationShowsTheZoneModeAgain() {
		AppUser u = user(zone(ZoneMode.EMERGENCY));
		u.setModeDismissedAt(ACTIVATED.minusDays(1));
		assertThat(UserDto.effectiveMode(u)).isEqualTo(ZoneMode.EMERGENCY);
	}

	@Test
	void normalZoneOrNoZoneIsNormal() {
		assertThat(UserDto.effectiveMode(user(zone(ZoneMode.NORMAL)))).isEqualTo(ZoneMode.NORMAL);
		assertThat(UserDto.effectiveMode(user(null))).isEqualTo(ZoneMode.NORMAL);
	}

	private static Zone zone(ZoneMode mode) {
		return Zone.builder().name("Z").district("SJL").mode(mode)
				.modeActivatedAt(mode == ZoneMode.NORMAL ? null : ACTIVATED).build();
	}

	private static AppUser user(Zone zone) {
		return AppUser.builder().phone("900000001").name("Vecina").zone(zone).build();
	}
}
