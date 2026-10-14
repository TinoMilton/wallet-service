package com.recargapay.wallet.observability;

import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.depositWithKey;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReplayMetricsTest extends IntegrationTest {

	@Autowired
	private MetricCounters counters;

	@Test
	@DisplayName("8: a replay increments the replay counter of its type")
	void replayIncrementsTheReplayCounterOfItsType() {
		WalletId wallet = aWallet();
		String key = "replay-" + UUID.randomUUID();
		depositWithKey(wallet, "50.00", key).then().statusCode(201);
		MetricCounters.Count depositReplays = counters.replaysOf("DEPOSIT");

		Response replayed = depositWithKey(wallet, "50.00", key);

		replayed.then().statusCode(201);
		depositReplays.increasedBy(1);
	}

	@Test
	@DisplayName("8: a first execution does not increment the replay counter")
	void firstExecutionDoesNotIncrementTheReplayCounter() {
		WalletId wallet = aWallet();
		MetricCounters.Count depositReplays = counters.replaysOf("DEPOSIT");

		Response created = deposit(wallet, "50.00");

		created.then().statusCode(201);
		depositReplays.increasedBy(0);
	}

	@Test
	@DisplayName("8: a key reused with a different request does not increment the replay counter")
	void reusedKeyDoesNotIncrementTheReplayCounter() {
		WalletId wallet = aWallet();
		String key = "reused-" + UUID.randomUUID();
		depositWithKey(wallet, "50.00", key).then().statusCode(201);
		MetricCounters.Count depositReplays = counters.replaysOf("DEPOSIT");

		Response rejected = depositWithKey(wallet, "100.00", key);

		assertRejected(rejected, 422, "IDEMPOTENCY_KEY_REUSED");
		assertBalance(wallet, "50.00");
		depositReplays.increasedBy(0);
	}
}
