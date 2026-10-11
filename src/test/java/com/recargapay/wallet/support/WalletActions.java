package com.recargapay.wallet.support;

import static io.restassured.RestAssured.given;

import com.recargapay.wallet.domain.WalletId;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.UUID;

/**
 * The HTTP actions a wallet test performs, hidden behind intention-revealing
 * names so a test body contains only what matters to its rule (techspec section
 * 9.3, rule 8). A later task adds its own helper class in its own file instead
 * of editing this one.
 */
public final class WalletActions {

	private WalletActions() {
	}

	public static WalletId aWallet() {
		return aWalletFor("user-" + UUID.randomUUID());
	}

	public static WalletId aWalletFor(String userId) {
		Response created = createWallet("{\"userId\": \"" + userId + "\"}");
		created.then().statusCode(201);
		return theWalletIdOf(created);
	}

	public static Response createWallet(String jsonBody) {
		return given().contentType(ContentType.JSON).body(jsonBody).when().post("/api/v1/wallets");
	}

	/** A POST whose content type promises JSON but whose body is absent. */
	public static Response createWalletWithoutABody() {
		return given().contentType(ContentType.JSON).when().post("/api/v1/wallets");
	}

	public static Response readBalance(WalletId wallet) {
		return given().when().get("/api/v1/wallets/{walletId}/balance", wallet.value());
	}

	public static WalletId theWalletIdOf(Response created) {
		return new WalletId(created.jsonPath().getUUID("walletId"));
	}
}
