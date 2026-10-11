package com.recargapay.wallet.outbox;

import com.recargapay.wallet.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The relay target of this build: one structured log line per event, with the
 * whole payload as JSON. Whatever encoder the logging configuration installs
 * renders the key-value pairs as fields.
 */
@Component
public class LoggingEventRelay implements EventRelay {

	private static final Logger log = LoggerFactory.getLogger(LoggingEventRelay.class);

	@Override
	public void relay(OutboxEvent event) {
		log.atInfo().setMessage("outbox event relayed").addKeyValue("eventId", event.id())
				.addKeyValue("eventType", event.eventType()).addKeyValue("aggregateType", event.aggregateType())
				.addKeyValue("aggregateId", event.aggregateId()).addKeyValue("occurredAt", event.occurredAt())
				.addKeyValue("payload", event.payload()).log();
	}
}
