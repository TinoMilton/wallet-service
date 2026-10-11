package com.recargapay.wallet.domain.exception;

/** A key already belongs to a different money movement. */
public final class IdempotencyKeyReusedException extends RuntimeException {

	public IdempotencyKeyReusedException() {
		super("The idempotency key was already used for a different request");
	}
}
