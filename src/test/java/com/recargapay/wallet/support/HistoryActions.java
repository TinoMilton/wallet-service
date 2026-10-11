package com.recargapay.wallet.support;

import static io.restassured.RestAssured.given;

import com.recargapay.wallet.domain.WalletId;
import io.restassured.response.Response;

/**
 * The HTTP actions a history test performs: the balance at a past instant and
 * the pages of a wallet's ledger. Task 7.0's helper, in its own file per the
 * test-support convention.
 */
public final class HistoryActions {

	private HistoryActions() {
	}

	/** Reads the balance at an instant, exactly as the caller sends it. */
	public static Response readBalanceAt(WalletId wallet, String at) {
		return given().queryParam("at", at).when().get("/api/v1/wallets/{walletId}/balance", wallet.value());
	}

	/** Reads the first page of the wallet's ledger with the default paging. */
	public static Response readEntries(WalletId wallet) {
		return given().when().get("/api/v1/wallets/{walletId}/entries", wallet.value());
	}

	/** Reads one page of the wallet's ledger with an explicit page and size. */
	public static Response readEntries(WalletId wallet, int page, int size) {
		return given().queryParams("page", page, "size", size).when().get("/api/v1/wallets/{walletId}/entries",
				wallet.value());
	}
}
