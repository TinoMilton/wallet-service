package com.recargapay.wallet.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's only source of time. Everything that needs "now" injects
 * this {@link Clock}, so tests can substitute a controllable one and nothing
 * reads the wall clock directly.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
