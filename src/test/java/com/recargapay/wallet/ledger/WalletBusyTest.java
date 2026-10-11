package com.recargapay.wallet.ledger;

import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import com.recargapay.wallet.support.LedgerAssertions;
import com.recargapay.wallet.support.WalletLockHolder;
import io.restassured.response.Response;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WalletBusyTest extends IntegrationTest {

	@Autowired
	private WalletLockHolder locks;

	@Autowired
	private LedgerAssertions ledger;

	@Test
	@DisplayName("BR-27: a deposit that cannot obtain the lock returns 503 without moving money")
	void busyDepositDoesNotMoveMoney() throws SQLException {
		WalletId wallet = aWalletWithBalance("10.00");

		Response rejected = locks.whileWalletLocked(wallet, () -> deposit(wallet, "5.00"));

		assertRejected(rejected, 503, "WALLET_BUSY");
		assertBalance(wallet, "10.00");
		ledger.assertLedgerEntryCount(wallet, 1);
	}

	@Test
	@DisplayName("BR-27: a withdrawal that cannot obtain the lock returns 503 without moving money")
	void busyWithdrawalDoesNotMoveMoney() throws SQLException {
		WalletId wallet = aWalletWithBalance("10.00");

		Response rejected = locks.whileWalletLocked(wallet, () -> withdraw(wallet, "5.00"));

		assertRejected(rejected, 503, "WALLET_BUSY");
		assertBalance(wallet, "10.00");
		ledger.assertLedgerEntryCount(wallet, 1);
	}
}
