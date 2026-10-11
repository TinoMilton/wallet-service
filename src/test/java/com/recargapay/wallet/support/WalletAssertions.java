package com.recargapay.wallet.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import com.recargapay.wallet.domain.WalletId;
import io.restassured.response.Response;

/**
 * The assertions a wallet test makes about what an observer can see: HTTP
 * responses, balances and rows in the real database (techspec section 9.3,
 * rules 6 to 9).
 */
public final class WalletAssertions {

	private WalletAssertions() {
	}

	public static void assertWalletCreatedWithBalance(Response created, String expectedBalance) {
		created.then().statusCode(201);
		assertThat(created.asString()).contains("\"balance\":" + expectedBalance + ",");
	}

	public static void assertBalance(WalletId wallet, String expectedBalance) {
		assertCurrentBalance(WalletActions.readBalance(wallet), expectedBalance);
	}

	public static void assertCurrentBalance(Response balanceResponse, String expectedBalance) {
		balanceResponse.then().statusCode(200);
		assertThat(balanceResponse.asString()).contains("\"balance\":" + expectedBalance + ",");
	}

	public static void assertRejected(Response response, int expectedStatus, String expectedCode) {
		response.then().statusCode(expectedStatus).header("Content-Type", containsString("application/problem+json"));
		assertThat(response.jsonPath().getString("code")).isEqualTo(expectedCode);
	}

	/**
	 * A rejected creation must leave no wallet behind (techspec section 9.3, rule
	 * 7).
	 */
	public static void assertNoWalletWasCreated(String isoInstant) {
		assertThat(TestDatabase.walletsCreatedAt(isoInstant)).isZero();
	}

	public static void assertWalletCountFor(String userId, int expectedCount) {
		assertThat(TestDatabase.walletsFor(userId)).isEqualTo(expectedCount);
	}
}
