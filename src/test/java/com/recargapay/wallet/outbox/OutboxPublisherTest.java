package com.recargapay.wallet.outbox;

import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.TestClock.theTimeIs;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.OutboxEvent;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.repository.OutboxEventRepository;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.OutboxAssertions;
import io.micrometer.core.instrument.MeterRegistry;
import io.restassured.response.Response;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The publisher tests drive a publishing run directly instead of waiting for
 * the schedule (techspec 9.3: no sleeps). The relay is a hand-written test
 * double of the outbox boundary: it implements EventRelay directly, records
 * every event it relays, and can be told which events to refuse. The publisher
 * under test is the real class wired to that double, run inside a transaction
 * because the hand-built instance has no transactional proxy. Nothing here
 * customises the context, so the whole suite keeps its single cached
 * application context.
 */
class OutboxPublisherTest extends IntegrationTest {

	@Autowired
	private OutboxEventRepository events;

	@Autowired
	private OutboxAssertions outbox;

	@Autowired
	private MeterRegistry meters;

	@Autowired
	private Clock clock;

	@Autowired
	private TransactionTemplate transactions;

	@Value("${wallet.outbox.batch-size}")
	private int batchSize;

	private final RecordingRelay relay = new RecordingRelay();

	@Test
	@DisplayName("A publishing run marks unpublished events as published, in occurred_at order")
	void runPublishesEventsInOccurredAtOrder() {
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");
		Response firstDeposit = deposit(wallet, "1.00");
		theTimeIs("2026-10-11T10:00:01Z");
		Response secondDeposit = deposit(wallet, "2.00");
		theTimeIs("2026-10-11T10:00:10Z");
		UUID first = firstDeposit.jsonPath().getUUID("operationId");
		UUID second = secondDeposit.jsonPath().getUUID("operationId");

		aPublishingRun();

		assertThat(relay.relayedOrderAmong(first, second)).containsExactly(first, second);
		outbox.assertPublishedAt(first, "2026-10-11T10:00:10Z");
		outbox.assertPublishedAt(second, "2026-10-11T10:00:10Z");
	}

	@Test
	@DisplayName("An event whose relay fails stays unpublished and does not stop the batch")
	void failedRelayLeavesItsEventUnpublished() {
		WalletId wallet = aWallet();
		Response failing = deposit(wallet, "1.00");
		Response published = deposit(wallet, "2.00");
		relay.failFor(failing.jsonPath().getUUID("operationId"));

		aPublishingRun();

		outbox.assertEventUnpublished(failing.jsonPath().getUUID("operationId"));
		outbox.assertPublishedAt(published.jsonPath().getUUID("operationId"), "2026-10-11T00:00:00Z");
	}

	@Test
	@DisplayName("A publishing run makes the unpublished gauge stop counting the test's event")
	void publishingRunStopsCountingTheTestsEvent() {
		WalletId wallet = aWallet();
		Response deposited = deposit(wallet, "1.00");
		deposited.then().statusCode(201);
		double unpublishedWithTheEvent = unpublishedGauge();

		relay.succeedOnly(deposited.jsonPath().getUUID("operationId"));
		aPublishingRun();

		// The run published only this test's event; the backlog it refused is
		// still counted.
		assertThat(unpublishedGauge()).isEqualTo(unpublishedWithTheEvent - 1.0);
	}

	@Test
	@DisplayName("wallet_outbox_lag_seconds is zero while the oldest unpublished event just occurred")
	void lagGaugeIsZeroWhileTheOldestEventJustOccurred() {
		// An instant earlier than every other test's event, so this test's event
		// is the oldest one.
		theTimeIs("1700-01-01T00:00:00Z");
		WalletId wallet = aWallet();
		Response deposited = deposit(wallet, "1.00");
		deposited.then().statusCode(201);

		assertThat(lagGauge()).isZero();

		// Publish the event, so no older unpublished event is left for other
		// tests' lag assertions.
		relay.succeedOnly(deposited.jsonPath().getUUID("operationId"));
		aPublishingRun();
	}

	private void aPublishingRun() {
		OutboxPublisher publisher = new OutboxPublisher(events, relay, clock, batchSize);
		transactions.executeWithoutResult(untouched -> publisher.publishPending());
	}

	private double unpublishedGauge() {
		return meters.get("wallet.outbox.unpublished").gauge().value();
	}

	private double lagGauge() {
		return meters.get("wallet.outbox.lag.seconds").gauge().value();
	}

	/**
	 * The hand-written test double of the outbox boundary (techspec 9.3: no project
	 * class is mocked). It records the events it relays and refuses the ones a test
	 * tells it to.
	 */
	static final class RecordingRelay implements EventRelay {

		private final List<UUID> relayedOperationIds = new ArrayList<>();
		private UUID failingOperationId;
		private Set<UUID> succeedingOnlyOperationIds;

		@Override
		public void relay(OutboxEvent event) {
			if (isRefused(event)) {
				throw new IllegalStateException("relay refused the event of operation " + event.aggregateId());
			}
			relayedOperationIds.add(event.aggregateId());
		}

		void failFor(UUID operationId) {
			this.failingOperationId = operationId;
		}

		/** Refuses every other event, so a run publishes only this test's events. */
		void succeedOnly(UUID... operationIds) {
			this.succeedingOnlyOperationIds = Set.copyOf(Arrays.asList(operationIds));
		}

		/**
		 * The order the relay saw the given operations in, ignoring the batch's other
		 * events.
		 */
		List<UUID> relayedOrderAmong(UUID... operationIds) {
			List<UUID> wanted = List.of(operationIds);
			return relayedOperationIds.stream().filter(wanted::contains).toList();
		}

		private boolean isRefused(OutboxEvent event) {
			if (event.aggregateId().equals(failingOperationId)) {
				return true;
			}
			return succeedingOnlyOperationIds != null && !succeedingOnlyOperationIds.contains(event.aggregateId());
		}
	}
}
