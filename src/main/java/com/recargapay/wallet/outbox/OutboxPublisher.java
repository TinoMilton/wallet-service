package com.recargapay.wallet.outbox;

import com.recargapay.wallet.domain.OutboxEvent;
import com.recargapay.wallet.repository.OutboxEventRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays the events a committed operation left in the outbox. Each run takes
 * one batch of unpublished events in occurred_at order, under a row lock that
 * makes competing instances skip the taken events, relays each one and stamps
 * it with the published time. Delivery is at-least-once: events carry the
 * operation id so consumers can deduplicate.
 */
@Component
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

	private final OutboxEventRepository events;
	private final EventRelay relay;
	private final Clock clock;
	private final int batchSize;

	public OutboxPublisher(OutboxEventRepository events, EventRelay relay, Clock clock,
			@Value("${wallet.outbox.batch-size}") int batchSize) {
		this.events = events;
		this.relay = relay;
		this.clock = clock;
		this.batchSize = batchSize;
	}

	/**
	 * The fixed-delay trigger. Tests call this method directly instead of waiting
	 * for the schedule (techspec section 9.3: no sleeps).
	 */
	@Scheduled(fixedDelayString = "${wallet.outbox.publish-delay}")
	@Transactional
	public void publishPending() {
		List<OutboxEvent> batch = events.lockNextUnpublishedBatch(batchSize);
		for (OutboxEvent event : batch) {
			relay(event);
		}
	}

	private void relay(OutboxEvent event) {
		try {
			relay.relay(event);
			event.markPublished(clock.instant());
		} catch (RuntimeException failure) {
			// One failed delivery must not roll back the batch: the event is simply left
			// unstamped, so it stays unpublished and is retried on the next run, while the
			// batch's other events commit as published.
			log.warn("outbox event {} was not relayed and stays unpublished", event.id(), failure);
		}
	}
}
