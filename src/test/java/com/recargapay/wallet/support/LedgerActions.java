package com.recargapay.wallet.support;

import static io.restassured.RestAssured.given;

import com.recargapay.wallet.domain.WalletId;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.UUID;

public final class LedgerActions {

	private LedgerActions() {
	}

	public static WalletId aWalletWithBalance(String amount) {
		WalletId wallet = WalletActions.aWallet();
		deposit(wallet, amount).then().statusCode(201);
		return wallet;
	}

	public static Response deposit(WalletId wallet, String amount) {
		return request(wallet, amount, UUID.randomUUID().toString(), "deposits");
	}

	public static Response depositWithKey(WalletId wallet, String amount, String key) {
		return request(wallet, amount, key, "deposits");
	}

	public static Response withdraw(WalletId wallet, String amount) {
		return request(wallet, amount, UUID.randomUUID().toString(), "withdrawals");
	}

	public static Response depositWithoutKey(WalletId wallet, String amount) {
		return given().contentType(ContentType.JSON).body("{\"amount\":" + amount + "}").when()
				.post("/api/v1/wallets/{walletId}/deposits", wallet.value());
	}

	public static Response withdrawWithoutKey(WalletId wallet, String amount) {
		return given().contentType(ContentType.JSON).body("{\"amount\":" + amount + "}").when()
				.post("/api/v1/wallets/{walletId}/withdrawals", wallet.value());
	}

	private static Response request(WalletId wallet, String amount, String key, String path) {
		return given().contentType(ContentType.JSON).header("Idempotency-Key", key).body("{\"amount\":" + amount + "}")
				.when().post("/api/v1/wallets/{walletId}/{operation}", wallet.value(), path);
	}
}
