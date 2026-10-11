package com.recargapay.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
	@Convert(converter = SignedAmountConverter.class)
	private SignedAmount amountSigned;

	@Column(name = "balance_after", precision = 19, scale = 2, nullable = false)
	@Convert(converter = BalanceConverter.class)
	private Balance balanceAfter;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LedgerEntry() {
	}

	private LedgerEntry(UUID id, Operation operation, Wallet wallet, long sequence, SignedAmount amountSigned,
			Balance balanceAfter, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "entry id");
		this.operation = Objects.requireNonNull(operation, "operation");
		this.wallet = Objects.requireNonNull(wallet, "wallet");
		this.sequence = sequence;
		this.amountSigned = Objects.requireNonNull(amountSigned, "signed amount");
		this.balanceAfter = Objects.requireNonNull(balanceAfter, "balance after");
		this.createdAt = Objects.requireNonNull(createdAt, "created at");
	}

	public static LedgerEntry credit(UUID id, Operation operation, Wallet wallet, long sequence, Money amount,
			Balance balanceAfter, Instant createdAt) {
		return record(id, operation, wallet, sequence, SignedAmount.credit(amount), balanceAfter, createdAt);
	}

	public static LedgerEntry debit(UUID id, Operation operation, Wallet wallet, long sequence, Money amount,
			Balance balanceAfter, Instant createdAt) {
		return record(id, operation, wallet, sequence, SignedAmount.debit(amount), balanceAfter, createdAt);
	}

	public static LedgerEntry record(UUID id, Operation operation, Wallet wallet, long sequence, SignedAmount amount,
			Balance balanceAfter, Instant createdAt) {
		return new LedgerEntry(id, operation, wallet, sequence, amount, balanceAfter, createdAt);
	}

	public long sequence() {
		return sequence;
	}

	public SignedAmount amountSigned() {
		return amountSigned;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Balance balanceAfter() {
		return balanceAfter;
	}

	/**
	 * The operation that caused this entry. The ledger page fetches the operation
	 * with its query; the lazy association could not be read once that read has
	 * ended.
	 */
	public OperationId operationId() {
		return new OperationId(operation.id());
	}

	/** The type of the operation that caused this entry. */
	public OperationType operationType() {
		return operation.type();
	}
}
