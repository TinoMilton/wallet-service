package com.recargapay.wallet.domain.exception;

public final class InvalidIdempotencyKeyException extends RuntimeException {

	public InvalidIdempotencyKeyException() {
		super("Idempotency-Key must be at most 128 characters");
	}
}
