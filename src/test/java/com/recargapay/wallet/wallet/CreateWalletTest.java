package com.recargapay.wallet.wallet;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.WalletTestActions;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

@Import(WalletTestActions.ControlledClockConfiguration.class)
class CreateWalletTest extends WalletTestActions {

	@Test
	@DisplayName("BR-01: a new wallet is created with a balance of zero")
	void aNewWalletIsCreatedWithZeroBalance() {
		// given
		theTimeIs("2026-10-11T12:00:00Z");

		// when
		Response created = createWallet("{\"userId\": \"user-123\"}");

		// then
		assertWalletCreated(created, "user-123", "0.00", "2026-10-11T12:00:00Z");
	}

	@Test
	@DisplayName("BR-02: a second wallet for the same user is rejected")
	void aSecondWalletForTheSameUserIsRejected() {
		// given
		WalletId existingWallet = aWalletFor("user-456");

		// when
		Response secondWallet = createWallet("{\"userId\": \"user-456\"}");

		// then
		assertRejected(secondWallet, 409, "WALLET_ALREADY_EXISTS");
		assertBalance(existingWallet, "0.00");
		assertWalletCountFor("user-456", 1);
	}

	@Test
	@DisplayName("techspec 6: a request without a userId is rejected with 400 INVALID_REQUEST")
	void aRequestWithoutAUserIdIsRejected() {
		// given
		// the body carries no userId

		// when
		Response rejected = createWallet("{}");

		// then
		assertRejected(rejected, 400, "INVALID_REQUEST");
	}

	@Test
	@DisplayName("techspec 6: a blank userId is rejected with 400 INVALID_REQUEST")
	void aBlankUserIdIsRejected() {
		// given
		// the body carries a blank userId

		// when
		Response rejected = createWallet("{\"userId\": \"\"}");

		// then
		assertRejected(rejected, 400, "INVALID_REQUEST");
	}
}
