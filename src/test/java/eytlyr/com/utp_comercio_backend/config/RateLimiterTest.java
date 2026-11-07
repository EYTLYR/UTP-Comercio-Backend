package eytlyr.com.utp_comercio_backend.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

	@Test
	void slidingWindowPerKey() {
		AtomicLong now = new AtomicLong(1_000_000);
		Clock clock = new Clock() {
			@Override
			public ZoneOffset getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(java.time.ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return Instant.ofEpochMilli(now.get());
			}
		};
		RateLimiter limiter = new RateLimiter(clock);
		for (int i = 0; i < 10; i++) {
			assertThat(limiter.tryAcquire("sos:ana", 10, Duration.ofMinutes(1))).isTrue();
		}
		assertThat(limiter.tryAcquire("sos:ana", 10, Duration.ofMinutes(1))).isFalse();
		assertThat(limiter.tryAcquire("sos:luis", 10, Duration.ofMinutes(1))).isTrue();

		now.addAndGet(Duration.ofSeconds(61).toMillis());
		assertThat(limiter.tryAcquire("sos:ana", 10, Duration.ofMinutes(1))).isTrue();
	}
}
