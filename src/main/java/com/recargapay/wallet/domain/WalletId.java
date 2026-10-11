package com.recargapay.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public record WalletId(@Column(name = "id", nullable = false) UUID value) {
	public WalletId {
		Objects.requireNonNull(value, "wallet id");
	}
}
