package com.recargapay.wallet.domain.exception;

/**
 * A page or size the audit endpoint cannot honour: negative, below one, or not
 * a number at all.
 */
public final class InvalidPageRequestException extends RuntimeException {

	public InvalidPageRequestException(String reason) {
		super(reason);
	}
}
