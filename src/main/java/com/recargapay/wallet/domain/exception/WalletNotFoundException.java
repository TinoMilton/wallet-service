package com.recargapay.wallet.domain.exception;

import java.util.UUID;

/**
 * BR-11: any operation on an unknown wallet is rejected.
 */
public final class WalletNotFoundException extends RuntimeException {

	public WalletNotFoundException(UUID walletId) {
		super("Wallet " + walletId + " does not exist");
	}
}
