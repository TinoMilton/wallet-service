package com.recargapay.wallet.observability;

import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProblemMetricsTest extends IntegrationTest {

	@Autowired
	private MetricCounters counters;

	@Test
	@DisplayName("8: a rejected request increments the problem counter of its own code")
	void rejectedRequestIncrementsTheCounterOfItsOwnCode() {
		WalletId wallet = aWalletWithBalance("10.00");
		MetricCounters.Count insufficientFunds = counters.problemsAnsweredWith("INSUFFICIENT_FUNDS");

		Response rejected = withdraw(wallet, "10.01");

		assertRejected(rejected, 422, "INSUFFICIENT_FUNDS");
		assertBalance(wallet, "10.00");
		insufficientFunds.increasedBy(1);
	}
}
