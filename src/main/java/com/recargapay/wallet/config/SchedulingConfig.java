package com.recargapay.wallet.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The scheduling behind the outbox publisher's fixed-delay trigger. Scheduling
 * can be switched off wholesale with wallet.outbox.scheduling-enabled=false,
 * which the tests do so they can call a publishing run directly (techspec 9.3:
 * no sleeps).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "wallet.outbox.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
