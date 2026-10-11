package com.recargapay.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public record IdempotencyKey(
		@Column(name = "idempotency_key", length = 128, nullable = false, unique = true) String value) {
	public IdempotencyKey {
		Objects.requireNonNull(value, "idempotency key");
	}
}
