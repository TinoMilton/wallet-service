package com.recargapay.wallet.wallet;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.WalletTestActions;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

@Import(WalletTestActions.ControlledClockConfiguration.class)
class CurrentBalanceTest extends WalletTestActions {

	@Test
	@DisplayName("BR-01: the current balance of a new wallet is zero, as of the moment of reading")
	void theCurrentBalanceOfANewWalletIsZero() {
		// given
		theTimeIs("2026-10-11T12:00:00Z");
		WalletId wallet = aWallet();

		// when
		Response balance = readBalance(wallet);

		// then
		balance.then().statusCode(200);
		assertThat(balance.asString()).contains("\"balance\":0.00,");
		assertThat(balance.jsonPath().getString("asOf")).isEqualTo("2026-10-11T12:00:00Z");
		assertThat(balance.jsonPath().getUUID("walletId")).isEqualTo(wallet.value());
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
}
