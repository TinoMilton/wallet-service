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
	@DisplayName("BR-22: reusing a deposit key on another wallet is rejected without changing either wallet")
	void differentWalletIsRejected() {
		WalletId originalWallet = aWallet();
		WalletId otherWallet = aWallet();
		String key = UUID.randomUUID().toString();
		depositWithKey(originalWallet, "10.00", key).then().statusCode(201);

		Response rejected = depositWithKey(otherWallet, "10.00", key);

		assertRejected(rejected, 422, "IDEMPOTENCY_KEY_REUSED");
		assertBalance(originalWallet, "10.00");
		assertBalance(otherWallet, "0.00");
		ledger.assertLedgerEntryCount(originalWallet, 1);
		ledger.assertLedgerEntryCount(otherWallet, 0);
		idempotency.assertOperationCount(key, 1);
		idempotency.assertEntryCount(key, 1);
		ledger.assertAuditInvariant(originalWallet);
		ledger.assertAuditInvariant(otherWallet);
	}

	@Test
	@DisplayName("BR-22: reusing a deposit key for a withdrawal is rejected without changing the wallet")
	void differentOperationTypeIsRejected() {
		WalletId wallet = aWallet();
		String key = UUID.randomUUID().toString();
		depositWithKey(wallet, "10.00", key).then().statusCode(201);

		Response rejected = withdrawWithKey(wallet, "10.00", key);

		assertRejected(rejected, 422, "IDEMPOTENCY_KEY_REUSED");
		assertBalance(wallet, "10.00");
		ledger.assertLedgerEntryCount(wallet, 1);
		idempotency.assertOperationCount(key, 1);
		idempotency.assertEntryCount(key, 1);
		ledger.assertAuditInvariant(wallet);
	}

	@Test
	@DisplayName("BR-22: reusing a transfer key with another receiver is rejected without moving money again")
	void differentTransferReceiverIsRejected() {
		WalletId sender = aWalletWithBalance("20.00");
		WalletId originalReceiver = aWallet();
		WalletId otherReceiver = aWallet();
		String key = UUID.randomUUID().toString();
		transferWithKey(sender, originalReceiver, "5.00", key).then().statusCode(201);

		Response rejected = transferWithKey(sender, otherReceiver, "5.00", key);

		assertRejected(rejected, 422, "IDEMPOTENCY_KEY_REUSED");
		assertBalance(sender, "15.00");
		assertBalance(originalReceiver, "5.00");
		assertBalance(otherReceiver, "0.00");
		ledger.assertLedgerEntryCount(sender, 2);
		ledger.assertLedgerEntryCount(originalReceiver, 1);
		ledger.assertLedgerEntryCount(otherReceiver, 0);
		idempotency.assertOperationCount(key, 1);
		idempotency.assertEntryCount(key, 2);
		ledger.assertAuditInvariant(sender);
		ledger.assertAuditInvariant(originalReceiver);
		ledger.assertAuditInvariant(otherReceiver);
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

	@Test
	@DisplayName("BR-23: a rejected transfer leaves the key free for a funded retry")
	void rejectedTransferCanBeRetried() {
		WalletId sender = aWallet();
		WalletId receiver = aWallet();
		String key = UUID.randomUUID().toString();
		Response rejected = transferWithKey(sender, receiver, "5.00", key);
		assertRejected(rejected, 422, "INSUFFICIENT_FUNDS");
		assertBalance(sender, "0.00");
		assertBalance(receiver, "0.00");
		ledger.assertLedgerEntryCount(sender, 0);
		ledger.assertLedgerEntryCount(receiver, 0);
		idempotency.assertOperationCount(key, 0);
		deposit(sender, "10.00").then().statusCode(201);

		Response retry = transferWithKey(sender, receiver, "5.00", key);

		retry.then().statusCode(201);
		assertBalance(sender, "5.00");
		assertBalance(receiver, "5.00");
		ledger.assertLedgerEntryCount(sender, 2);
		ledger.assertLedgerEntryCount(receiver, 1);
		idempotency.assertOperationCount(key, 1);
		idempotency.assertEntryCount(key, 2);
		ledger.assertAuditInvariant(sender);
		ledger.assertAuditInvariant(receiver);
	}

	private void assertSameResult(Response original, Response replay) {
		original.then().statusCode(201);
		replay.then().statusCode(201);
		assertThat(replay.asString()).isEqualTo(original.asString());
		assertThat(replay.jsonPath().getUUID("operationId")).isEqualTo(original.jsonPath().getUUID("operationId"));
	}
}
