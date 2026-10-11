package com.recargapay.wallet.domain.exception;

public final class MissingIdempotencyKeyException extends RuntimeException {

	public MissingIdempotencyKeyException() {
		super("Idempotency-Key header is required");
	}
}
