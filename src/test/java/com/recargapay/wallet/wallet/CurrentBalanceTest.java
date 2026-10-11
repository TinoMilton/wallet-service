package com.recargapay.wallet.wallet;

import static com.recargapay.wallet.support.TestClock.theTimeIs;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletActions.readBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertCurrentBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CurrentBalanceTest extends IntegrationTest {

	@Test
	@DisplayName("BR-01: the current balance of a new wallet is zero")
	void theCurrentBalanceOfANewWalletIsZero() {
		// given
		theTimeIs("2026-10-11T13:00:00Z");
		WalletId wallet = aWallet();

		// when
		Response balance = readBalance(wallet);

		// then
		assertCurrentBalance(balance, "0.00");
	}

	@Test
	@DisplayName("BR-11: the balance of an unknown wallet is rejected with 404")
	void theBalanceOfAnUnknownWalletIsRejected() {
		// given
		WalletId unknownWallet = new WalletId(UUID.randomUUID());

		// when
		Response rejected = readBalance(unknownWallet);

		// then
		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
	}

	@Test
	@DisplayName("BR-11: a wallet id in the URL that is not a UUID is treated as an unknown wallet")
	void aWalletIdThatIsNotAUuidIsTreatedAsAnUnknownWallet() {
		// given
		// the URL carries a wallet id that is not a UUID

		// when
		Response rejected = given().when().get("/api/v1/wallets/{walletId}/balance", "not-a-uuid");

		// then
		assertRejected(rejected, 404, "WALLET_NOT_FOUND");
	}

	@Test
	@DisplayName("the balance response is stamped with the time of the reading")
	void theBalanceResponseIsStampedWithTheTimeOfTheReading() {
		// given
		theTimeIs("2026-10-11T13:05:00Z");
		WalletId wallet = aWallet();

		// when
		Response balance = readBalance(wallet);

		// then
		assertThat(balance.jsonPath().getString("asOf")).isEqualTo("2026-10-11T13:05:00Z");
	}

	@Test
	@DisplayName("the balance response echoes the wallet it was asked about")
	void theBalanceResponseEchoesTheWalletItWasAskedAbout() {
		// given
		theTimeIs("2026-10-11T13:10:00Z");
		WalletId wallet = aWallet();

		// when
		Response balance = readBalance(wallet);

		// then
		assertThat(balance.jsonPath().getUUID("walletId")).isEqualTo(wallet.value());
	}

	@Test
	@DisplayName("an unknown route keeps its 404 with code ROUTE_NOT_FOUND")
	void anUnknownRouteKeepsIts404() {
		// given
		// no route answers this path

		// when
		Response rejected = given().when().get("/api/v1/no-such-route");

		// then
		assertRejected(rejected, 404, "ROUTE_NOT_FOUND");
	}

	@Test
	@DisplayName("a method the balance route does not accept keeps its 405 with code METHOD_NOT_ALLOWED")
	void aMethodTheBalanceRouteDoesNotAcceptKeepsIts405() {
		// given
		WalletId wallet = aWallet();

		// when
		Response rejected = given().when().delete("/api/v1/wallets/{walletId}/balance", wallet.value());

		// then
		assertRejected(rejected, 405, "METHOD_NOT_ALLOWED");
	}
}
