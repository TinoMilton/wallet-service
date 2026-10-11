package com.recargapay.wallet.support;

import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The application's clock, under the tests' control. The shared test
 * configuration installs it as the application's {@link Clock}, so the whole
 * application, server included, takes its time from {@link #CLOCK} and a test
 * never depends on the wall clock (techspec section 9.3, rule 5).
 */
public final class TestClock {

	/**
	 * The instant every test starts from; a test moves the clock to the instant its
	 * rule needs.
	 */
	public static final String BASELINE = "2026-10-11T00:00:00Z";

	public static final MutableClock CLOCK = MutableClock.at(Instant.parse(BASELINE));

	private TestClock() {
	}

	/**
	 * Moves the application's time to the given instant; the service stamps
	 * everything with it.
	 */
	public static void theTimeIs(String isoInstant) {
		CLOCK.setTo(Instant.parse(isoInstant));
	}

	static void resetToBaseline() {
		CLOCK.setTo(Instant.parse(BASELINE));
	}

	/**
	 * The single shared configuration that replaces the real clock with the
	 * controllable one.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	public static class ClockConfiguration {

		@Bean
		@Primary
		Clock controlledClock() {
			return CLOCK;
		}
	}
}
