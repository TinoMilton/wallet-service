package com.recargapay.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "wallet")
public class Wallet {

	@EmbeddedId
	private WalletId id;

	@Column(name = "user_id", length = 64, nullable = false, unique = true)
	private String userId;

	@Column(name = "balance", precision = 19, scale = 2, nullable = false)
	@Convert(converter = BalanceConverter.class)
	private Balance balance;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Wallet() {
	}

	public Wallet(WalletId id, String userId, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "wallet id");
		this.userId = Objects.requireNonNull(userId, "user id");
		this.balance = Balance.zero();
		this.createdAt = Objects.requireNonNull(createdAt, "created at");
	}

	public WalletId id() {
		return id;
	}

	public String userId() {
		return userId;
	}

	public Balance balance() {
		return balance;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
