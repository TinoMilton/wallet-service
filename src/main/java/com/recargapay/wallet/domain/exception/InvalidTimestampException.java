package com.recargapay.wallet.domain.exception;

/**
 * BR-16: an instant a historical query may not use: malformed, without an
 * offset, or in the future.
 */
public final class InvalidTimestampException extends RuntimeException {

	public InvalidTimestampException(String reason) {
		super(reason);
	}
}
