package com.recargapay.wallet.wallet;

import static com.recargapay.wallet.support.TestClock.theTimeIs;
import static com.recargapay.wallet.support.TestDatabase.acceptWalletInsertsAgain;
import static com.recargapay.wallet.support.TestDatabase.rejectWalletInsertsOnAnotherConstraint;
import static com.recargapay.wallet.support.WalletActions.createWallet;
import static com.recargapay.wallet.support.WalletActions.createWalletWithoutABody;
import static com.recargapay.wallet.support.WalletActions.theWalletIdOf;
import static com.recargapay.wallet.support.WalletAssertions.assertNoWalletWasCreated;
import static com.recargapay.wallet.support.WalletAssertions.assertRejected;
import static com.recargapay.wallet.support.WalletAssertions.assertWalletCountFor;
import static com.recargapay.wallet.support.WalletAssertions.assertWalletCreatedWithBalance;
import static com.recargapay.wallet.support.WalletActions.aWalletFor;
import static com.recargapay.wallet.support.WalletActions.readBalance;
import static com.recargapay.wallet.support.WalletAssertions.assertBalance;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateWalletTest extends IntegrationTest {

	@Test
	@DisplayName("BR-01: a new wallet is created with a balance of zero")
	void aNewWalletIsCreatedWithZeroBalance() {
		// given
		theTimeIs("2026-10-11T12:00:00Z");

		// when
		Response created = createWallet("{\"userId\": \"user-100\"}");

		// then
		assertWalletCreatedWithBalance(created, "0.00");
	}

	@Test
	@DisplayName("BR-02: a second wallet for the same user is rejected")
	void aSecondWalletForTheSameUserIsRejected() {
		// given
		theTimeIs("2026-10-11T12:05:00Z");
		WalletId existingWallet = aWalletFor("user-200");

		// when
		Response secondWallet = createWallet("{\"userId\": \"user-200\"}");

		// then
		assertRejected(secondWallet, 409, "WALLET_ALREADY_EXISTS");
		assertBalance(existingWallet, "0.00");
		assertWalletCountFor("user-200", 1);
	}

	@Test
	@DisplayName("a request without a userId is rejected with 400 INVALID_REQUEST")
	void aRequestWithoutAUserIdIsRejected() {
		// given
		theTimeIs("2026-10-11T12:10:00Z");

		// when
		Response rejected = createWallet("{}");

		// then
		assertRejected(rejected, 400, "INVALID_REQUEST");
		assertNoWalletWasCreated("2026-10-11T12:10:00Z");
	}

	@Test
	@DisplayName("a blank userId is rejected with 400 INVALID_REQUEST")
	void aBlankUserIdIsRejected() {
		// given
		theTimeIs("2026-10-11T12:15:00Z");

		// when
		Response rejected = createWallet("{\"userId\": \"\"}");

		// then
		assertRejected(rejected, 400, "INVALID_REQUEST");
		assertNoWalletWasCreated("2026-10-11T12:15:00Z");
	}

	@Test
	@DisplayName("a userId longer than 64 characters is rejected with 400 INVALID_REQUEST")
	void aUserIdLongerThan64CharactersIsRejected() {
		// given
		theTimeIs("2026-10-11T12:20:00Z");

		// when
		Response rejected = createWallet(
				"{\"userId\": \"user-123456789012345678901234567890123456789012345678901234567890\"}");

		// then
		assertRejected(rejected, 400, "INVALID_REQUEST");
		assertNoWalletWasCreated("2026-10-11T12:20:00Z");
	}

	@Test
	@DisplayName("a request without a body is rejected with 400 INVALID_REQUEST")
	void aRequestWithoutABodyIsRejected() {
		// given
		theTimeIs("2026-10-11T12:25:00Z");

		// when
		Response rejected = createWalletWithoutABody();

		// then
		assertRejected(rejected, 400, "INVALID_REQUEST");
		assertNoWalletWasCreated("2026-10-11T12:25:00Z");
	}

	@Test
	@DisplayName("a request with a malformed body is rejected with 400 INVALID_REQUEST")
	void aRequestWithAMalformedBodyIsRejected() {
		// given
		theTimeIs("2026-10-11T12:30:00Z");

		// when
		Response rejected = createWallet("{ not json");

		// then
		assertRejected(rejected, 400, "INVALID_REQUEST");
		assertNoWalletWasCreated("2026-10-11T12:30:00Z");
	}

	@Test
	@DisplayName("a request whose body is not JSON is rejected with 415 UNSUPPORTED_MEDIA_TYPE")
	void aRequestWhoseBodyIsNotJsonIsRejected() {
		// given
		theTimeIs("2026-10-11T12:35:00Z");

		// when
		Response rejected = given().contentType(ContentType.TEXT).body("user-600").when().post("/api/v1/wallets");

		// then
		assertRejected(rejected, 415, "UNSUPPORTED_MEDIA_TYPE");
		assertNoWalletWasCreated("2026-10-11T12:35:00Z");
	}

	@Test
	@DisplayName("a failure other than the one-wallet-per-user rule returns 500 INTERNAL_ERROR without internal detail")
	void aFailureOtherThanTheOneWalletPerUserRuleReturns500() {
		// given
		// a wallet insert fails on a constraint that is not the one-wallet-per-user
		// rule
		rejectWalletInsertsOnAnotherConstraint("user-789");
		try {
			// when
			Response rejected = createWallet("{\"userId\": \"user-789\"}");

			// then
			assertRejected(rejected, 500, "INTERNAL_ERROR");
			assertThat(rejected.jsonPath().getString("detail")).isEqualTo("An unexpected error occurred");
			assertWalletCountFor("user-789", 0);
		} finally {
			acceptWalletInsertsAgain();
		}
	}

	@Test
	@DisplayName("a created wallet echoes the userId it was created for")
	void aCreatedWalletEchoesItsUserId() {
		// given
		theTimeIs("2026-10-11T12:45:00Z");

		// when
		Response created = createWallet("{\"userId\": \"user-300\"}");

		// then
		assertThat(created.jsonPath().getString("userId")).isEqualTo("user-300");
	}

	@Test
	@DisplayName("the wallet id of the response identifies the created wallet")
	void theWalletIdOfTheResponseIdentifiesTheCreatedWallet() {
		// given
		theTimeIs("2026-10-11T12:50:00Z");

		// when
		Response created = createWallet("{\"userId\": \"user-400\"}");

		// then
		WalletId walletId = theWalletIdOf(created);
		readBalance(walletId).then().statusCode(200);
	}

	@Test
	@DisplayName("a created wallet reports its creation time")
	void aCreatedWalletReportsItsCreationTime() {
		// given
		theTimeIs("2026-10-11T12:55:00Z");

		// when
		Response created = createWallet("{\"userId\": \"user-500\"}");

		// then
		assertThat(created.jsonPath().getString("createdAt")).isEqualTo("2026-10-11T12:55:00Z");
	}
}
