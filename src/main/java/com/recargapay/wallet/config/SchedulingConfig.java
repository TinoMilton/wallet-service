package com.recargapay.wallet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The scheduling behind the outbox publisher's fixed-delay trigger. Tests
 * neutralise the trigger through its delay property and call a publishing run
 * directly.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
