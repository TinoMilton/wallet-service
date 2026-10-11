package com.recargapay.wallet.outbox;

import com.recargapay.wallet.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * The outbox gauges from techspec section 8: how many events wait for
 * publication, and how long the oldest of them has waited. Both read the live
 * table on every evaluation.
 */
@Component
public class OutboxMetrics {

	private final OutboxEventRepository events;
	private final Clock clock;

	public OutboxMetrics(MeterRegistry meters, OutboxEventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
		Gauge.builder("wallet.outbox.unpublished", events, repository -> repository.countByPublishedAtIsNull())
				.description("Outbox events not yet relayed").register(meters);
		Gauge.builder("wallet.outbox.lag.seconds", this, OutboxMetrics::lagSeconds)
				.description("Age of the oldest unpublished outbox event").register(meters);
	}

	private double lagSeconds() {
		return events.findFirstByPublishedAtIsNullOrderByOccurredAtAsc()
				.map(oldest -> Duration.between(oldest.occurredAt(), clock.instant()).toMillis() / 1000.0).orElse(0.0);
	}
}
