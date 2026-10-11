package com.recargapay.wallet.ledger;

import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.LedgerActions.depositWithoutKey;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.LedgerActions.withdrawWithoutKey;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.LedgerAssertions;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WithdrawalTest extends IntegrationTest {

	@Autowired
	private LedgerAssertions ledger;

	@Test
	@DisplayName("BR-06: a withdrawal decreases the balance by exactly the amount")
	void withdrawalDecreasesBalance() {
		WalletId wallet = aWalletWithBalance("100.00");

		Response withdrawn = withdraw(wallet, "30.25");

		ledger.assertOperation(withdrawn, wallet, "WITHDRAWAL", "30.25", "69.75");
		assertBalance(wallet, "69.75");
		ledger.assertEntry(wallet, 2, "-30.25", "69.75");
	}

	@Test
	@DisplayName("BR-07: a withdrawal greater than the balance is rejected and nothing changes")
	void insufficientFundsLeaveNoTrace() {
		WalletId wallet = aWalletWithBalance("100.00");

		Response rejected = withdraw(wallet, "100.01");

		assertRejected(rejected, 422, "INSUFFICIENT_FUNDS");
		assertBalance(wallet, "100.00");
		ledger.assertLedgerEntryCount(wallet, 1);
		ledger.assertAuditInvariant(wallet);
	}

	@Test
	@DisplayName("BR-11: a withdrawal on an unknown wallet is rejected")
	void unknownWalletRejectsWithdrawal() {
		WalletId unknown = new WalletId(UUID.randomUUID());

		Response rejected = withdraw(unknown, "1.00");

		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
		ledger.assertLedgerEntryCount(unknown, 0);
	}

	@Test
	@DisplayName("BR-20: a deposit without an idempotency key is rejected")
	void missingDepositKey() {
		WalletId wallet = aWallet();

		Response rejected = depositWithoutKey(wallet, "1.00");

		assertRejected(rejected, 400, "MISSING_IDEMPOTENCY_KEY");
		assertBalance(wallet, "0.00");
		ledger.assertLedgerEntryCount(wallet, 0);
	}

	@Test
	@DisplayName("BR-20: a withdrawal without an idempotency key is rejected")
	void missingWithdrawalKey() {
		WalletId wallet = aWalletWithBalance("10.00");

		Response rejected = withdrawWithoutKey(wallet, "1.00");

		assertRejected(rejected, 400, "MISSING_IDEMPOTENCY_KEY");
		assertBalance(wallet, "10.00");
		ledger.assertLedgerEntryCount(wallet, 1);
	}
}
