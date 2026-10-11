package com.recargapay.wallet.domain.exception;

public final class SameWalletTransferException extends RuntimeException {

	public SameWalletTransferException() {
		super("A wallet cannot transfer funds to itself");
	}
}
