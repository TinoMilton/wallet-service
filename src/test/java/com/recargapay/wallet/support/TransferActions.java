package com.recargapay.wallet.support;

import static io.restassured.RestAssured.given;

import com.recargapay.wallet.domain.WalletId;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.UUID;

public final class TransferActions {

	private TransferActions() {
	}

	public static Response transfer(WalletId from, WalletId to, String amount) {
		return transfer(from.value().toString(), to.value().toString(), amount);
	}

	public static Response transfer(String from, String to, String amount) {
		return request(from, to, amount, UUID.randomUUID().toString());
	}

	public static Response transferWithoutKey(WalletId from, WalletId to, String amount) {
		return request(from.value().toString(), to.value().toString(), amount, null);
	}

	public static Response opposingTransferForCaller(WalletId first, WalletId second, String amount, int caller,
			int callersEachWay) {
		return caller < callersEachWay ? transfer(first, second, amount) : transfer(second, first, amount);
	}

	private static Response request(String from, String to, String amount, String key) {
		String body = "{\"fromWalletId\":\"" + from + "\",\"toWalletId\":\"" + to + "\",\"amount\":" + amount + "}";
		var request = given().contentType(ContentType.JSON).body(body);
		if (key != null) {
			request.header("Idempotency-Key", key);
		}
		return request.when().post("/api/v1/transfers");
	}
}
