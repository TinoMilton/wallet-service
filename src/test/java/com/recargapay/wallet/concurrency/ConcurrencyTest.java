package com.recargapay.wallet.concurrency;

import static com.recargapay.wallet.support.ConcurrentCallers.runConcurrently;
import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.LedgerAssertions;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

class ConcurrencyTest extends IntegrationTest {

	@Autowired
	private LedgerAssertions ledger;

	@Test
	@Timeout(120)
	@DisplayName("BR-24: of 100 simultaneous full-balance withdrawals exactly one succeeds")
	void concurrentWithdrawalsNeverOverspend() throws Exception {
		WalletId wallet = aWalletWithBalance("100.00");

		List<Response> responses = runConcurrently(100, caller -> withdraw(wallet, "100.00"));

		assertResponses(responses, 1, 99);
		assertBalance(wallet, "0.00");
		ledger.assertLedgerEntryCount(wallet, 2);
		ledger.assertEntry(wallet, 2, "-100.00", "0.00");
		ledger.assertAuditInvariant(wallet);
	}

	@Test
	@Timeout(120)
	@DisplayName("BR-29: 100 simultaneous deposits all apply with contiguous sequences")
	void concurrentDepositsAllApply() throws Exception {
		WalletId wallet = aWallet();

		List<Response> responses = runConcurrently(100, caller -> deposit(wallet, "1.00"));

		assertResponses(responses, 100, 0);
		assertBalance(wallet, "100.00");
		ledger.assertContiguousSequences(wallet, 100);
		ledger.assertAuditInvariant(wallet);
	}

	private void assertResponses(List<Response> responses, int created, int insufficient) {
		assertThat(responses).hasSize(100);
		assertThat(responses.stream().filter(response -> response.statusCode() == 201).count()).isEqualTo(created);
		assertThat(responses.stream()
				.filter(response -> response.statusCode() == 422
						&& "INSUFFICIENT_FUNDS".equals(response.jsonPath().getString("code")))
				.count()).isEqualTo(insufficient);
		assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isIn(201, 422));
	}
}
