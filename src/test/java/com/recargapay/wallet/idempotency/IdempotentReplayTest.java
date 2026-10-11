package com.recargapay.wallet.idempotency;

import static com.recargapay.wallet.support.IdempotencyActions.depositWithKey;
import static com.recargapay.wallet.support.IdempotencyActions.transferWithKey;
import static com.recargapay.wallet.support.IdempotencyActions.withdrawWithKey;
import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.TestClock.theTimeIs;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IdempotencyAssertions;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.LedgerAssertions;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IdempotentReplayTest extends IntegrationTest {

	@Autowired
	private LedgerAssertions ledger;

	@Autowired
	private IdempotencyAssertions idempotency;

	@Test
	@DisplayName("BR-21: repeating a deposit returns its original result and credits only once")
	void depositReplaysOriginalBalanceAndTime() {
		WalletId wallet = aWallet();
		String key = UUID.randomUUID().toString();
		Response original = depositWithKey(wallet, "10.00", key);
		deposit(wallet, "5.00").then().statusCode(201);
		theTimeIs("2026-10-11T12:00:00Z");

		Response replay = depositWithKey(wallet, "10.00", key);

		assertSameResult(original, replay);
		assertBalance(wallet, "15.00");
		ledger.assertLedgerEntryCount(wallet, 2);
		idempotency.assertOperationCount(key, 1);
		idempotency.assertEntryCount(key, 1);
		ledger.assertAuditInvariant(wallet);
	}

	@Test
	@DisplayName("BR-21: repeating a withdrawal returns its original result and debits only once")
	void withdrawalReplaysOriginalBalanceAndTime() {
		WalletId wallet = aWalletWithBalance("20.00");
		String key = UUID.randomUUID().toString();
		Response original = withdrawWithKey(wallet, "5.00", key);
		deposit(wallet, "2.00").then().statusCode(201);
		theTimeIs("2026-10-11T12:00:00Z");

		Response replay = withdrawWithKey(wallet, "5.00", key);

		assertSameResult(original, replay);
		assertBalance(wallet, "17.00");
		ledger.assertLedgerEntryCount(wallet, 3);
		idempotency.assertOperationCount(key, 1);
		idempotency.assertEntryCount(key, 1);
		ledger.assertAuditInvariant(wallet);
	}

	@Test
	@DisplayName("BR-21: repeating a transfer returns its original result and moves money only once")
	void transferReplaysOriginalSenderBalanceAndTime() {
		WalletId sender = aWalletWithBalance("20.00");
		WalletId receiver = aWallet();
		String key = UUID.randomUUID().toString();
		Response original = transferWithKey(sender, receiver, "5.00", key);
		deposit(sender, "2.00").then().statusCode(201);
		theTimeIs("2026-10-11T12:00:00Z");

		Response replay = transferWithKey(sender, receiver, "5.00", key);

		assertSameResult(original, replay);
		assertBalance(sender, "17.00");
		assertBalance(receiver, "5.00");
		ledger.assertLedgerEntryCount(sender, 3);
		ledger.assertLedgerEntryCount(receiver, 1);
		idempotency.assertOperationCount(key, 1);
		idempotency.assertEntryCount(key, 2);
		ledger.assertAuditInvariant(sender);
		ledger.assertAuditInvariant(receiver);
	}

	@Test
	@DisplayName("BR-22: reusing a deposit key with another amount is rejected without changing money")
	void differentAmountIsRejected() {
		WalletId wallet = aWallet();
		String key = UUID.randomUUID().toString();
		depositWithKey(wallet, "10.00", key).then().statusCode(201);

		Response rejected = depositWithKey(wallet, "11.00", key);

		assertRejected(rejected, 422, "IDEMPOTENCY_KEY_REUSED");
		assertBalance(wallet, "10.00");
		ledger.assertLedgerEntryCount(wallet, 1);
		idempotency.assertOperationCount(key, 1);
		idempotency.assertEntryCount(key, 1);
		ledger.assertAuditInvariant(wallet);
	}

	@Test
	@DisplayName("BR-23: a rejected withdrawal leaves the key free for a funded retry")
	void rejectedWithdrawalCanBeRetried() {
		WalletId wallet = aWallet();
		String key = UUID.randomUUID().toString();
		Response rejected = withdrawWithKey(wallet, "10.00", key);
		assertRejected(rejected, 422, "INSUFFICIENT_FUNDS");
		assertBalance(wallet, "0.00");
		ledger.assertLedgerEntryCount(wallet, 0);
		idempotency.assertOperationCount(key, 0);
		deposit(wallet, "10.00").then().statusCode(201);

		Response retry = withdrawWithKey(wallet, "10.00", key);

		retry.then().statusCode(201);
		assertBalance(wallet, "0.00");
		ledger.assertLedgerEntryCount(wallet, 2);
		idempotency.assertOperationCount(key, 1);
		idempotency.assertEntryCount(key, 1);
		ledger.assertAuditInvariant(wallet);
	}

	private void assertSameResult(Response original, Response replay) {
		original.then().statusCode(201);
		replay.then().statusCode(201);
		assertThat(replay.asString()).isEqualTo(original.asString());
		assertThat(replay.jsonPath().getUUID("operationId")).isEqualTo(original.jsonPath().getUUID("operationId"));
	}
}
