package com.recargapay.wallet.domain.exception;

/**
 * BR-02: a user can have only one wallet, so a second creation for the same
 * user is rejected.
 */
public final class WalletAlreadyExistsException extends RuntimeException {

	public WalletAlreadyExistsException(String userId) {
		super("A wallet already exists for user " + userId);
	}
}
