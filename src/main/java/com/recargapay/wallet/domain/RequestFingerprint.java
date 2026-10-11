package com.recargapay.wallet.domain;

import java.util.Objects;

/**
 * The SHA-256 fingerprint of what a money request asks for: the operation type,
 * the wallet or wallets and the amount. Two requests sharing an idempotency key
 * must also share this fingerprint; a difference means the key was reused for a
 * different request.
 */
public record RequestFingerprint(String value) {

	public RequestFingerprint {
		Objects.requireNonNull(value, "request fingerprint");
	}
}
