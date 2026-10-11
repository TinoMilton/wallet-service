package com.recargapay.wallet.ledger;

import static com.recargapay.wallet.support.HistoryActions.readEntries;
import static com.recargapay.wallet.support.HistoryAssertions.assertEntry;
import static com.recargapay.wallet.support.HistoryAssertions.assertEntryCount;
import static com.recargapay.wallet.support.HistoryAssertions.assertPageMetadata;
import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
import static com.recargapay.wallet.support.LedgerActions.aWalletWithBalance;
import static com.recargapay.wallet.support.TestClock.theTimeIs;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The audit endpoint: a wallet's ledger, newest first, one page at a time
 * (techspec section 7). Each operation is stamped with its own whole second, so
 * the page's contents are literal.
 */
class LedgerEntriesTest extends IntegrationTest {

	@Test
	@DisplayName("the ledger page lists the entries of a wallet newest first")
	void theLedgerPageListsTheEntriesNewestFirst() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");
		deposit(wallet, "10.00").then().statusCode(201);
		theTimeIs("2026-10-11T11:00:00Z");
		withdraw(wallet, "2.50").then().statusCode(201);

		// when
		Response page = readEntries(wallet);

		// then
		assertThat(page.jsonPath().getUUID("walletId")).isEqualTo(wallet.value());
		assertPageMetadata(page, 0, 20, 2);
		assertEntryCount(page, 2);
		assertEntry(page, 0, "2", "WITHDRAWAL", "-2.50", "7.50", "2026-10-11T11:00:00Z");
		assertEntry(page, 1, "1", "DEPOSIT", "10.00", "10.00", "2026-10-11T10:00:00Z");
	}

	@Test
	@DisplayName("the ledger page serialises each signed amount as a number with two decimals")
	void theLedgerPageSerialisesSignedAmountsWithTwoDecimals() {
		// given
		WalletId wallet = aWalletWithBalance("10.00");
		withdraw(wallet, "2.50").then().statusCode(201);

		// when
		Response page = readEntries(wallet);

		// then
		assertThat(page.asString()).contains("\"amountSigned\":-2.50", "\"balanceAfter\":7.50");
	}

	@Test
	@DisplayName("the requested page size is respected")
	void theRequestedPageSizeIsRespected() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");
		deposit(wallet, "1.00").then().statusCode(201);
		theTimeIs("2026-10-11T11:00:00Z");
		deposit(wallet, "2.00").then().statusCode(201);
		theTimeIs("2026-10-11T12:00:00Z");
		deposit(wallet, "5.00").then().statusCode(201);

		// when
		Response page = readEntries(wallet, 0, 2);

		// then
		assertPageMetadata(page, 0, 2, 3);
		assertEntryCount(page, 2);
		assertEntry(page, 0, "3", "DEPOSIT", "5.00", "8.00", "2026-10-11T12:00:00Z");
		assertEntry(page, 1, "2", "DEPOSIT", "2.00", "3.00", "2026-10-11T11:00:00Z");
	}

	@Test
	@DisplayName("a page beyond the first returns the remaining entries")
	void aPageBeyondTheFirstReturnsTheRemainingEntries() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");
		deposit(wallet, "1.00").then().statusCode(201);
		theTimeIs("2026-10-11T11:00:00Z");
		deposit(wallet, "2.00").then().statusCode(201);
		theTimeIs("2026-10-11T12:00:00Z");
		deposit(wallet, "5.00").then().statusCode(201);

		// when
		Response page = readEntries(wallet, 1, 2);

		// then
		assertPageMetadata(page, 1, 2, 3);
		assertEntryCount(page, 1);
		assertEntry(page, 0, "1", "DEPOSIT", "1.00", "1.00", "2026-10-11T10:00:00Z");
	}

	@Test
	@DisplayName("a page size above the maximum of one hundred is capped to it")
	void aPageSizeAboveTheMaximumIsCappedToIt() {
		// given
		WalletId wallet = aWallet();

		// when
		Response page = readEntries(wallet, 0, 500);

		// then
		assertPageMetadata(page, 0, 100, 0);
		assertEntryCount(page, 0);
	}

	@Test
	@DisplayName("BR-11: the entries of an unknown wallet are rejected with 404")
	void theEntriesOfAnUnknownWalletAreRejected() {
		// given
		WalletId unknownWallet = new WalletId(UUID.randomUUID());

		// when
		Response rejected = readEntries(unknownWallet);

		// then
		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
	}
}
