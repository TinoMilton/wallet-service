package com.recargapay.wallet.ledger;

import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.TransferActions.transfer;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.LedgerAssertions;
import com.recargapay.wallet.support.TransferAssertions;
import io.restassured.response.Response;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuditInvariantRandomisedTest extends IntegrationTest {

	@Autowired
	private LedgerAssertions ledger;

	@Autowired
	private TransferAssertions transfers;

	@Test
	@DisplayName("BR-19: a fixed-seed mix preserves every audit invariant and conserves money")
	void randomisedMixedOperationsPreserveEveryWalletsAuditInvariant() {
		WalletId[] wallets = {aWalletWithBalance("200.00"), aWalletWithBalance("200.00"), aWalletWithBalance("200.00"),
				aWalletWithBalance("200.00")};
		Random random = new Random(20261011L);
		int deposits = 0;
		int withdrawals = 0;

		for (int operation = 0; operation < 120; operation++) {
			int sender = random.nextInt(wallets.length);
			Response response = switch (random.nextInt(3)) {
				case 0 -> {
					deposits++;
					yield deposit(wallets[sender], "1.00");
				}
				case 1 -> {
					withdrawals++;
					yield withdraw(wallets[sender], "1.00");
				}
				default -> transfer(wallets[sender],
						wallets[(sender + 1 + random.nextInt(wallets.length - 1)) % wallets.length], "1.00");
			};
			response.then().statusCode(201);
		}

		for (WalletId wallet : wallets) {
			ledger.assertAuditInvariant(wallet);
		}
		Balance expectedTotal = Balance.of(Money.of((800 + deposits - withdrawals) + ".00"));
		transfers.assertTotalBalance(wallets, expectedTotal);
	}
}
