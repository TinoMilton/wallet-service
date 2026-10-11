package com.recargapay.wallet.outbox;

import com.recargapay.wallet.domain.OutboxEvent;

/**
 * Delivers one outbox event to the outside world. In this build the only
 * implementation writes a structured log line; in production it would be a
 * message broker (techspec section 7). Delivery is at-least-once: consumers
 * deduplicate on the operation id.
 */
public interface EventRelay {

	/**
	 * Delivers one event. An implementation signals a failed delivery with an
	 * exception; the publisher then leaves the event unpublished and tries again on
	 * the next run.
	 */
	void relay(OutboxEvent event);
}
