package com.recargapay.wallet.domain.exception;

public final class InvalidAmountException extends RuntimeException {

	public InvalidAmountException(Throwable cause) {
		super(cause.getMessage(), cause);
	}
}
