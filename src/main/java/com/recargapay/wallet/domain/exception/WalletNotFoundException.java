package com.recargapay.wallet.domain.exception;

import com.recargapay.wallet.domain.WalletId;

/**
 * BR-11: any operation on an unknown wallet is rejected. A wallet id in the URL
 * that is not a valid UUID is an unknown wallet too, so the exception also
 * accepts the id exactly as the caller sent it.
 */
public final class WalletNotFoundException extends RuntimeException {

	public WalletNotFoundException(WalletId walletId) {
		super("Wallet " + walletId.value() + " does not exist");
	}

	public WalletNotFoundException(String walletIdAsTheCallerSentIt) {
		super("Wallet " + walletIdAsTheCallerSentIt + " does not exist");
	}
}
