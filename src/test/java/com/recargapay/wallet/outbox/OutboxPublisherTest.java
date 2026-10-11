package com.recargapay.wallet.outbox;

import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.TestClock.theTimeIs;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.OutboxEvent;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.OutboxAssertions;
import io.micrometer.core.instrument.MeterRegistry;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * The publisher tests drive a publishing run directly instead of waiting for
 * the schedule (techspec 9.3: no sleeps). The relay is a hand-written test
 * double of an interface the outbox owns at its boundary: it records every
 * event it sees, can be told to fail for one operation, and still writes the
 * real structured log line. Only this class imports it, so the rest of the
 * suite keeps the single cached application context.
 */
@Import(OutboxPublisherTest.RelayConfiguration.class)
class OutboxPublisherTest extends IntegrationTest {

	@Autowired
	private OutboxPublisher publisher;

	@Autowired
	private OutboxAssertions outbox;

	@Autowired
	private MeterRegistry meters;

	@Autowired
	private RecordingRelay relay;

	@BeforeEach
	void resetTheRecordingRelay() {
		relay.reset();
	}

	@Test
	@DisplayName("A publishing run marks unpublished events as published, in occurred_at order")
	void runPublishesEventsInOccurredAtOrder() {
		// Instants earlier than every other test's events, so this test's two events
		// come
		// first in the batch whatever the shared database already holds.
		theTimeIs("1700-01-01T00:00:00Z");
		WalletId wallet = aWallet();
		Response firstDeposit = deposit(wallet, "1.00");
		theTimeIs("1700-01-01T00:00:01Z");
		Response secondDeposit = deposit(wallet, "2.00");
		theTimeIs("1700-01-01T00:00:10Z");

		publisher.publishPending();

		assertThat(relay.relayedOperationIds()).startsWith(firstDeposit.jsonPath().getUUID("operationId"),
				secondDeposit.jsonPath().getUUID("operationId"));
		outbox.assertPublishedAt(firstDeposit.jsonPath().getUUID("operationId"), "1700-01-01T00:00:10Z");
		outbox.assertPublishedAt(secondDeposit.jsonPath().getUUID("operationId"), "1700-01-01T00:00:10Z");
	}

	@Test
	@DisplayName("An event whose relay fails stays unpublished and does not stop the batch")
	void failedRelayLeavesItsEventUnpublished() {
		theTimeIs("1801-01-01T00:00:00Z");
		WalletId wallet = aWallet();
		Response failing = deposit(wallet, "1.00");
		Response published = deposit(wallet, "2.00");

		relay.failFor(failing.jsonPath().getUUID("operationId"));
		publisher.publishPending();

		outbox.assertEventUnpublished(failing.jsonPath().getUUID("operationId"));
		outbox.assertPublishedAt(published.jsonPath().getUUID("operationId"), "1801-01-01T00:00:00Z");
	}

	@Test
	@DisplayName("Both outbox gauges read zero when every event has been published")
	void gaugesReadZeroWhenEveryEventIsPublished() {
		WalletId wallet = aWallet();
		deposit(wallet, "1.00").then().statusCode(201);

		// One run relays the whole shared backlog, because the test batch size covers
		// the suite.
		publisher.publishPending();

		assertThat(meters.get("wallet.outbox.unpublished").gauge().value()).isZero();
		assertThat(meters.get("wallet.outbox.lag.seconds").gauge().value()).isZero();
	}

	/** Supplies the hand-written relay double for this class only. */
	@TestConfiguration(proxyBeanMethods = false)
	static class RelayConfiguration {

		@Bean
		@Primary
		RecordingRelay recordingRelay() {
			return new RecordingRelay();
		}
	}

	static final class RecordingRelay extends LoggingEventRelay {

		private final List<UUID> relayedOperationIds = new ArrayList<>();
		private UUID failForOperationId;

		@Override
		public void relay(OutboxEvent event) {
			if (event.aggregateId().equals(failForOperationId)) {
				throw new IllegalStateException("relay refused the event of operation " + failForOperationId);
			}
			relayedOperationIds.add(event.aggregateId());
			super.relay(event);
		}

		void failFor(UUID operationId) {
			this.failForOperationId = operationId;
		}

		void reset() {
			relayedOperationIds.clear();
			failForOperationId = null;
		}

		List<UUID> relayedOperationIds() {
			return List.copyOf(relayedOperationIds);
		}
	}
}
