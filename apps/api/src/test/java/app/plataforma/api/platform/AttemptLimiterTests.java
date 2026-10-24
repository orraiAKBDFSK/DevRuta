package app.plataforma.api.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AttemptLimiterTests {

	/** Reloj manual para avanzar el tiempo sin esperar. */
	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	@Test
	void blocksAfterTooManyEventsAndReportsRetryAfter() {
		MutableClock clock = new MutableClock();
		AttemptLimiter limiter = new AttemptLimiter(3, Duration.ofMinutes(15), clock);

		for (int i = 0; i < 3; i++) {
			limiter.checkAllowed("a@example.com");
			limiter.record("a@example.com");
		}

		assertThatThrownBy(() -> limiter.checkAllowed("a@example.com")).isInstanceOfSatisfying(ApiException.class,
				e -> {
					assertThat(e.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
					assertThat(e.getRetryAfterSeconds()).isBetween(1L, 15L * 60 + 1);
				});
		assertThat(limiter.isExhausted("a@example.com")).isTrue();
		assertThatCode(() -> limiter.checkAllowed("otro@example.com")).doesNotThrowAnyException();
	}

	@Test
	void allowsAgainAfterTheWindowPasses() {
		MutableClock clock = new MutableClock();
		AttemptLimiter limiter = new AttemptLimiter(2, Duration.ofMinutes(15), clock);
		limiter.record("a@example.com");
		limiter.record("a@example.com");

		clock.advance(Duration.ofMinutes(16));

		assertThatCode(() -> limiter.checkAllowed("a@example.com")).doesNotThrowAnyException();
	}

	@Test
	void resetClearsTheCounter() {
		MutableClock clock = new MutableClock();
		AttemptLimiter limiter = new AttemptLimiter(2, Duration.ofMinutes(15), clock);
		limiter.record("a@example.com");
		limiter.record("a@example.com");

		limiter.reset("a@example.com");

		assertThat(limiter.isExhausted("a@example.com")).isFalse();
	}

}
