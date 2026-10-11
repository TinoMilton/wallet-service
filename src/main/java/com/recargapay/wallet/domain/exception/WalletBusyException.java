package com.recargapay.wallet.domain.exception;

public final class WalletBusyException extends RuntimeException {

	public WalletBusyException(Throwable cause) {
		super("Wallet lock could not be obtained in time", cause);
	}
}
