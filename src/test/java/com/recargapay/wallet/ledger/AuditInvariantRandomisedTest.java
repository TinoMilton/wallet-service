package com.recargapay.wallet.ledger;

import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.TransferActions.transfer;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.LedgerAssertions;
import io.restassured.response.Response;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuditInvariantRandomisedTest extends IntegrationTest {

	@Autowired
	private LedgerAssertions ledger;

	@Test
	@DisplayName("BR-19: a fixed-seed mix of deposits, withdrawals and transfers preserves every audit invariant")
	void randomisedMixedOperationsPreserveEveryWalletsAuditInvariant() {
		WalletId[] wallets = {aWalletWithBalance("200.00"), aWalletWithBalance("200.00"), aWalletWithBalance("200.00"),
				aWalletWithBalance("200.00")};
		Random random = new Random(20261011L);

		for (int operation = 0; operation < 120; operation++) {
			int sender = random.nextInt(wallets.length);
			Response response = switch (random.nextInt(3)) {
				case 0 -> deposit(wallets[sender], "1.00");
				case 1 -> withdraw(wallets[sender], "1.00");
				default -> transfer(wallets[sender],
						wallets[(sender + 1 + random.nextInt(wallets.length - 1)) % wallets.length], "1.00");
			};
			response.then().statusCode(201);
		}

		for (WalletId wallet : wallets) {
			ledger.assertAuditInvariant(wallet);
		}
	}
}
