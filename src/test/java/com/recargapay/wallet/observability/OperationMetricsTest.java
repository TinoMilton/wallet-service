package com.recargapay.wallet.observability;

import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.depositWithKey;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.MetricsAssertions;
import com.recargapay.wallet.support.WalletLockHolder;
import io.restassured.response.Response;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OperationMetricsTest extends IntegrationTest {

	@Autowired
	private MetricsAssertions metrics;

	@Autowired
	private WalletLockHolder locks;

	@Test
	@DisplayName("9.1: a successful deposit is counted as one DEPOSIT success")
	void successfulDepositIsCountedAsASuccess() {
		WalletId wallet = aWallet();
		MetricsAssertions.OperationCount successfulDeposits = metrics.countOf("DEPOSIT", "SUCCESS");

		Response created = deposit(wallet, "50.00");

		created.then().statusCode(201);
		successfulDeposits.increasedBy(1);
	}

	@Test
	@DisplayName("9.1: a rejected withdrawal is counted as one WITHDRAWAL rejection")
	void rejectedWithdrawalIsCountedAsARejection() {
		WalletId wallet = aWalletWithBalance("10.00");
		MetricsAssertions.OperationCount rejectedWithdrawals = metrics.countOf("WITHDRAWAL", "REJECTED");

		Response rejected = withdraw(wallet, "10.01");

		assertRejected(rejected, 422, "INSUFFICIENT_FUNDS");
		assertBalance(wallet, "10.00");
		rejectedWithdrawals.increasedBy(1);
	}

	@Test
	@DisplayName("9.1: a withdrawal that times out on the wallet lock is counted as one WITHDRAWAL busy")
	void lockTimeoutIsCountedAsBusy() throws SQLException {
		WalletId wallet = aWalletWithBalance("10.00");
		MetricsAssertions.OperationCount busyWithdrawals = metrics.countOf("WITHDRAWAL", "BUSY");

		Response rejected = locks.whileWalletLocked(wallet, () -> withdraw(wallet, "5.00"));

		assertRejected(rejected, 503, "WALLET_BUSY");
		assertBalance(wallet, "10.00");
		busyWithdrawals.increasedBy(1);
	}

	@Test
	@DisplayName("9.1: an idempotent replay is counted as a success, not a rejection")
	void idempotentReplayIsCountedAsASuccess() {
		WalletId wallet = aWallet();
		String key = "replay-" + UUID.randomUUID();
		MetricsAssertions.OperationCount successfulDeposits = metrics.countOf("DEPOSIT", "SUCCESS");

		depositWithKey(wallet, "50.00", key).then().statusCode(201);
		depositWithKey(wallet, "50.00", key).then().statusCode(201);

		successfulDeposits.increasedBy(2);
	}

	@Test
	@DisplayName("9.1: a deposit is timed as one DEPOSIT operation")
	void depositIsTimedByType() {
		WalletId wallet = aWallet();
		MetricsAssertions.Timings depositTimings = metrics.timingsOf("DEPOSIT");

		Response created = deposit(wallet, "50.00");

		created.then().statusCode(201);
		depositTimings.increasedBy(1);
	}
}
