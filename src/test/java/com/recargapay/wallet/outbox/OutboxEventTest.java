package com.recargapay.wallet.outbox;

import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.depositWithKey;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.TestClock.theTimeIs;
import static com.recargapay.wallet.support.TransferActions.transfer;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.LedgerAssertions;
import com.recargapay.wallet.support.OutboxAssertions;
import io.micrometer.core.instrument.MeterRegistry;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OutboxEventTest extends IntegrationTest {

	@Autowired
	private OutboxAssertions outbox;

	@Autowired
	private LedgerAssertions ledger;

	@Autowired
	private MeterRegistry meters;

	@Test
	@DisplayName("BR-28: a successful deposit emits exactly one event with the operation id")
	void depositEmitsExactlyOneEvent() {
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");

		Response deposited = deposit(wallet, "50.25");

		UUID operationId = deposited.jsonPath().getUUID("operationId");
		outbox.assertOneEventFor(operationId, "FundsDeposited");
		outbox.assertPayloadCarries(operationId, "operationId", operationId.toString());
		outbox.assertPayloadCarries(operationId, "walletId", wallet.value().toString());
		outbox.assertPayloadCarries(operationId, "amount", "50.25");
		outbox.assertPayloadCarries(operationId, "occurredAt", "2026-10-11T10:00:00Z");
		outbox.assertEventUnpublished(operationId);
	}

	@Test
	@DisplayName("BR-28: a successful withdrawal emits exactly one FundsWithdrawn event")
	void withdrawalEmitsExactlyOneEvent() {
		WalletId wallet = aWalletWithBalance("100.00");

		Response withdrawn = withdraw(wallet, "40.00");

		outbox.assertOneEventFor(withdrawn.jsonPath().getUUID("operationId"), "FundsWithdrawn");
	}

	@Test
	@DisplayName("BR-28: a successful transfer emits exactly one event")
	void transferEmitsExactlyOneEvent() {
		WalletId sender = aWalletWithBalance("100.00");
		WalletId receiver = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");

		Response transferred = transfer(sender, receiver, "40.00");

		UUID operationId = transferred.jsonPath().getUUID("operationId");
		outbox.assertOneEventFor(operationId, "FundsTransferred");
		outbox.assertPayloadCarries(operationId, "operationId", operationId.toString());
		outbox.assertPayloadCarries(operationId, "fromWalletId", sender.value().toString());
		outbox.assertPayloadCarries(operationId, "toWalletId", receiver.value().toString());
		outbox.assertPayloadCarries(operationId, "amount", "40.00");
		outbox.assertPayloadCarries(operationId, "occurredAt", "2026-10-11T10:00:00Z");
	}

	@Test
	@DisplayName("BR-28: a rejected withdrawal emits no event")
	void rejectedWithdrawalEmitsNoEvent() {
		WalletId wallet = aWalletWithBalance("100.00");

		Response rejected = withdraw(wallet, "100.01");

		assertRejected(rejected, 422, "INSUFFICIENT_FUNDS");
		assertBalance(wallet, "100.00");
		ledger.assertLedgerEntryCount(wallet, 1);
		outbox.assertNoEventOfTypeFor("FundsWithdrawn", wallet);
	}

	@Test
	@DisplayName("BR-28: an idempotent replay emits no second event")
	void replayEmitsNoSecondEvent() {
		WalletId wallet = aWallet();
		String key = UUID.randomUUID().toString();
		Response original = depositWithKey(wallet, "25.00", key);

		Response replayed = depositWithKey(wallet, "25.00", key);

		original.then().statusCode(201);
		replayed.then().statusCode(201);
		assertThat(replayed.jsonPath().getUUID("operationId")).isEqualTo(original.jsonPath().getUUID("operationId"));
		outbox.assertOneEventFor(original.jsonPath().getUUID("operationId"), "FundsDeposited");
	}

	@Test
	@DisplayName("wallet_outbox_unpublished reports the number of unpublished events")
	void unpublishedGaugeReportsUnpublishedEvents() {
		double unpublishedBefore = unpublishedGauge();
		WalletId wallet = aWallet();

		deposit(wallet, "10.00").then().statusCode(201);

		// The gauge reads the live table: one more committed deposit, one more
		// unpublished event.
		assertThat(unpublishedGauge() - unpublishedBefore).isEqualTo(1.0);
	}

	@Test
	@DisplayName("wallet_outbox_lag_seconds reports the age of the oldest unpublished event")
	void lagGaugeReportsAgeOfOldestUnpublishedEvent() {
		// An instant earlier than every other test's events, so this test's event is
		// the oldest.
		theTimeIs("1800-01-01T00:00:00Z");
		WalletId wallet = aWallet();
		deposit(wallet, "1.00").then().statusCode(201);

		theTimeIs("1800-01-01T00:00:07Z");

		assertThat(lagGauge()).isEqualTo(7.0);
	}

	private double unpublishedGauge() {
		return meters.get("wallet.outbox.unpublished").gauge().value();
	}

	private double lagGauge() {
		return meters.get("wallet.outbox.lag.seconds").gauge().value();
	}
}
