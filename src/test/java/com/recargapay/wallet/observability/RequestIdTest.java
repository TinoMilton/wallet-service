package com.recargapay.wallet.observability;

import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletActions.readBalance;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestIdTest extends IntegrationTest {

	@Test
	@DisplayName("9.2: a request without a request id gets one, and the response returns it")
	void responseCarriesAGeneratedRequestId() {
		WalletId wallet = aWallet();

		Response balance = readBalance(wallet);

		balance.then().statusCode(200);
		assertThat(balance.getHeader("X-Request-Id")).isNotBlank();
	}

	@Test
	@DisplayName("9.2: a request id sent by the caller is returned unchanged")
	void incomingRequestIdIsEchoed() {
		WalletId wallet = aWallet();

		Response balance = given().header("X-Request-Id", "the-callers-id").when()
				.get("/api/v1/wallets/{walletId}/balance", wallet.value());

		balance.then().statusCode(200);
		assertThat(balance.getHeader("X-Request-Id")).isEqualTo("the-callers-id");
	}
}
