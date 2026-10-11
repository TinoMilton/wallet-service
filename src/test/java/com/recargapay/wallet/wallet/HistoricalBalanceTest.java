package com.recargapay.wallet.wallet;

import static com.recargapay.wallet.support.HistoryActions.readBalanceAt;
import static com.recargapay.wallet.support.HistoryAssertions.assertBalanceAt;
import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.LedgerActions.withdraw;
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
 * The balance of a wallet at a past instant (BR-13 to BR-16). PostgreSQL keeps
 * microsecond precision, so every operation is recorded at a whole second and
 * the boundaries sit one microsecond either side of it: the comparisons are
 * exact, not timing luck.
 */
class HistoricalBalanceTest extends IntegrationTest {

	@Test
	@DisplayName("BR-13: the balance at exactly an entry's timestamp includes that entry")
	void balanceAtAnEntryTimestampIncludesThatEntry() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");
		deposit(wallet, "10.00").then().statusCode(201);

		// when
		Response balance = readBalanceAt(wallet, "2026-10-11T10:00:00Z");

		// then
		assertBalanceAt(balance, "10.00");
	}

	@Test
	@DisplayName("BR-13: the balance one microsecond before an entry's timestamp excludes that entry")
	void balanceJustBeforeAnEntryTimestampExcludesThatEntry() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");
		deposit(wallet, "10.00").then().statusCode(201);

		// when
		Response balance = readBalanceAt(wallet, "2026-10-11T09:59:59.999999Z");

		// then
		assertBalanceAt(balance, "0.00");
	}

	@Test
	@DisplayName("BR-13: the balance just after the last entry equals the current balance")
	void balanceJustAfterTheLastEntryEqualsTheCurrentBalance() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");
		deposit(wallet, "10.00").then().statusCode(201);
		theTimeIs("2026-10-11T11:00:00Z");
		withdraw(wallet, "2.50").then().statusCode(201);
		theTimeIs("2026-10-11T12:00:00Z");

		// when
		Response balance = readBalanceAt(wallet, "2026-10-11T11:00:00.000001Z");

		// then
		assertBalanceAt(balance, "7.50");
	}

	@Test
	@DisplayName("BR-14: the balance of an existing wallet with no operations up to the instant is zero")
	void balanceOfAWalletWithNoOperationsUpToTheInstantIsZero() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");

		// when
		Response balance = readBalanceAt(wallet, "2026-10-11T09:30:00Z");

		// then
		assertBalanceAt(balance, "0.00");
	}

	@Test
	@DisplayName("BR-15: a historical query before the wallet was created is rejected with 404")
	void aHistoricalQueryBeforeTheWalletWasCreatedIsRejected() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();

		// when
		Response rejected = readBalanceAt(wallet, "2026-10-11T08:00:00Z");

		// then
		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
	}

	@Test
	@DisplayName("BR-16: a historical query in the future is rejected with 400")
	void aHistoricalQueryInTheFutureIsRejected() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();

		// when
		Response rejected = readBalanceAt(wallet, "2026-10-11T09:00:01Z");

		// then
		assertRejected(rejected, 400, "INVALID_TIMESTAMP");
	}

	@Test
	@DisplayName("BR-16: a historical query with a malformed instant is rejected with 400")
	void aHistoricalQueryWithAMalformedInstantIsRejected() {
		// given
		WalletId wallet = aWallet();

		// when
		Response rejected = readBalanceAt(wallet, "not-a-timestamp");

		// then
		assertRejected(rejected, 400, "INVALID_TIMESTAMP");
	}

	@Test
	@DisplayName("BR-16: a historical query without an offset is rejected with 400")
	void aHistoricalQueryWithoutAnOffsetIsRejected() {
		// given
		WalletId wallet = aWallet();

		// when
		Response rejected = readBalanceAt(wallet, "2026-10-11T10:00:00");

		// then
		assertRejected(rejected, 400, "INVALID_TIMESTAMP");
	}

	@Test
	@DisplayName("BR-11: a historical query on an unknown wallet is rejected with 404")
	void aHistoricalQueryOnAnUnknownWalletIsRejected() {
		// given
		WalletId unknownWallet = new WalletId(UUID.randomUUID());

		// when
		Response rejected = readBalanceAt(unknownWallet, "2025-01-01T00:00:00Z");

		// then
		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
	}

	@Test
	@DisplayName("the historical balance response reports the queried instant as its asOf")
	void theHistoricalBalanceResponseReportsTheQueriedInstant() {
		// given
		theTimeIs("2026-10-11T09:00:00Z");
		WalletId wallet = aWallet();
		theTimeIs("2026-10-11T10:00:00Z");

		// when
		Response balance = readBalanceAt(wallet, "2026-10-11T09:30:00Z");

		// then
		assertThat(balance.jsonPath().getString("asOf")).isEqualTo("2026-10-11T09:30:00Z");
	}
}
