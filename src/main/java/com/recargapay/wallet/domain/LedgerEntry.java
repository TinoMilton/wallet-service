package com.recargapay.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_entry")
public class LedgerEntry {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "operation_id", nullable = false)
	private Operation operation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "wallet_id", nullable = false)
	private Wallet wallet;

	@Column(name = "seq", nullable = false)
	private long sequence;

	@Column(name = "amount_signed", precision = 19, scale = 2, nullable = false)
	private BigDecimal amountSigned;

	@Column(name = "balance_after", precision = 19, scale = 2, nullable = false)
	private BigDecimal balanceAfter;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LedgerEntry() {
	}

	public LedgerEntry(UUID id, Operation operation, Wallet wallet, long sequence, Money amount, boolean debit,
			Balance balanceAfter, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "entry id");
		this.operation = Objects.requireNonNull(operation, "operation");
		this.wallet = Objects.requireNonNull(wallet, "wallet");
		this.sequence = sequence;
		this.amountSigned = debit ? amount.decimalValue().negate() : amount.decimalValue();
		this.balanceAfter = balanceAfter.decimalValue();
		this.createdAt = Objects.requireNonNull(createdAt, "created at");
	}
}
