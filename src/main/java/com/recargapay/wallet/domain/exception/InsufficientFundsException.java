package com.recargapay.wallet.domain.exception;

public final class InsufficientFundsException extends RuntimeException {

	public InsufficientFundsException() {
		super("Wallet has insufficient funds");
	}
}
