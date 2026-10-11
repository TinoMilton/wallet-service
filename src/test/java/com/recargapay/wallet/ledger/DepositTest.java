package com.recargapay.wallet.ledger;

import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.depositWithKey;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.TestClock.theTimeIs;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.LedgerAssertions;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DepositTest extends IntegrationTest {

	@Autowired
	private LedgerAssertions ledger;

	@Test
	@DisplayName("BR-05: a deposit increases the balance by exactly the amount")
	void depositIncreasesBalance() {
		WalletId wallet = aWallet();

		Response deposited = deposit(wallet, "50.25");

		ledger.assertOperation(deposited, wallet, "DEPOSIT", "50.25", "50.25");
		assertBalance(wallet, "50.25");
	}

	@Test
	@DisplayName("BR-11: a deposit on an unknown wallet is rejected")
	void unknownWalletRejectsDeposit() {
		WalletId unknown = new WalletId(UUID.randomUUID());

		Response rejected = deposit(unknown, "10.00");

		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
		ledger.assertLedgerEntryCount(unknown, 0);
	}

	@Test
	@DisplayName("BR-12: the current balance equals the latest operation's balance")
	void currentBalanceMatchesLatestOperation() {
		WalletId wallet = aWallet();
		deposit(wallet, "10.00").then().statusCode(201);

		Response latest = deposit(wallet, "2.50");

		assertBalance(wallet, "12.50");
		assertThat(latest.asString()).contains("\"balance\":12.50");
		ledger.assertEntry(wallet, 2, "2.50", "12.50");
	}

	@Test
	@DisplayName("BR-17: each operation records one ledger entry with amount, sequence and resulting balance")
	void entriesRecordOperations() {
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");
		Response first = deposit(wallet, "10.00");

		Response second = deposit(wallet, "2.50");

		ledger.assertOperation(first, wallet, "DEPOSIT", "10.00", "10.00");
		ledger.assertOperation(second, wallet, "DEPOSIT", "2.50", "12.50");
		ledger.assertEntry(wallet, 1, "10.00", "10.00");
		ledger.assertEntry(wallet, 2, "2.50", "12.50");
		ledger.assertContiguousSequences(wallet, 2);
	}

	@Test
	@DisplayName("BR-17: entries with the same clock instant have increasing timestamps in sequence order")
	void entriesWithUnchangedClockKeepTimeOrder() {
		WalletId wallet = aWallet();
		deposit(wallet, "1.00").then().statusCode(201);

		Response second = deposit(wallet, "1.00");

		second.then().statusCode(201);
		ledger.assertRecordedTimeIncreasesWithSequence(wallet);
	}

	@Test
	@DisplayName("BR-19: cached balance, latest ledger balance and sum of entries agree")
	void ledgerReconcilesWithWallet() {
		WalletId wallet = aWallet();
		deposit(wallet, "20.00");
		deposit(wallet, "2.50");
		withdraw(wallet, "1.25").then().statusCode(201);

		ledger.assertAuditInvariant(wallet);
	}

	@Test
	@DisplayName("BR-17: an entry records the clock instant when it follows an earlier entry")
	void entryRecordsClockInstant() {
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");
		deposit(wallet, "1.00").then().statusCode(201);
		theTimeIs("2026-10-11T10:01:00Z");

		Response second = deposit(wallet, "1.00");

		second.then().statusCode(201);
		ledger.assertRecordedAt(wallet, 2, "2026-10-11T10:01:00Z");
	}

	@Test
	@DisplayName("BR-03: a zero deposit amount is rejected without changing the wallet")
	void zeroAmountIsRejected() {
		WalletId wallet = aWallet();

		Response rejected = deposit(wallet, "0.00");

		assertRejected(rejected, 400, "INVALID_AMOUNT");
		assertBalance(wallet, "0.00");
		ledger.assertLedgerEntryCount(wallet, 0);
	}

	@Test
	@DisplayName("BR-04: a deposit amount with more than two decimals is rejected without rounding")
	void extraDecimalsAreRejected() {
		WalletId wallet = aWallet();

		Response rejected = deposit(wallet, "1.001");

		assertRejected(rejected, 400, "INVALID_AMOUNT");
		assertBalance(wallet, "0.00");
		ledger.assertLedgerEntryCount(wallet, 0);
	}

	@Test
	@DisplayName("A key longer than the operation column is rejected before moving money")
	void oversizedKeyIsRejected() {
		WalletId wallet = aWallet();

		Response rejected = depositWithKey(wallet, "1.00", "k".repeat(129));

		assertRejected(rejected, 400, "INVALID_REQUEST");
		assertBalance(wallet, "0.00");
		ledger.assertLedgerEntryCount(wallet, 0);
	}

	@Test
	@DisplayName("BR-20: a blank idempotency key is rejected without moving money")
	void blankKeyIsRejected() {
		WalletId wallet = aWallet();

		Response rejected = depositWithKey(wallet, "1.00", "   ");

		assertRejected(rejected, 400, "MISSING_IDEMPOTENCY_KEY");
		assertBalance(wallet, "0.00");
		ledger.assertLedgerEntryCount(wallet, 0);
	}

	@Test
	@DisplayName("A successful deposit stores its idempotency key and request fingerprint")
	void depositStoresRequestIdentity() {
		WalletId wallet = aWallet();
		String key = UUID.randomUUID().toString();

		Response deposited = depositWithKey(wallet, "1.00", key);

		deposited.then().statusCode(201);
		ledger.assertStoredRequest(deposited, wallet, key, "1.00");
	}
}
