package com.recargapay.wallet.support;

import static io.restassured.RestAssured.given;

import com.recargapay.wallet.domain.WalletId;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

public final class IdempotencyActions {

	private IdempotencyActions() {
	}

	public static Response depositWithKey(WalletId wallet, String amount, String key) {
		return moneyRequest(wallet, amount, key, "deposits");
	}

	public static Response withdrawWithKey(WalletId wallet, String amount, String key) {
		return moneyRequest(wallet, amount, key, "withdrawals");
	}

	public static Response transferWithKey(WalletId from, WalletId to, String amount, String key) {
		String body = "{\"fromWalletId\":\"" + from.value() + "\",\"toWalletId\":\"" + to.value() + "\",\"amount\":"
				+ amount + "}";
		return given().contentType(ContentType.JSON).header("Idempotency-Key", key).body(body).when()
				.post("/api/v1/transfers");
	}

	private static Response moneyRequest(WalletId wallet, String amount, String key, String path) {
		return given().contentType(ContentType.JSON).header("Idempotency-Key", key).body("{\"amount\":" + amount + "}")
				.when().post("/api/v1/wallets/{walletId}/{operation}", wallet.value(), path);
	}
}
