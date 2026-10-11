package com.recargapay.wallet.domain;

import java.math.BigDecimal;

/**
 * A non-negative balance, including the zero balance after a full withdrawal.
 */
public final class Balance {

	private final BigDecimal amount;

	private Balance(BigDecimal amount) {
		this.amount = amount;
	}

	public static Balance zero() {
		return new Balance(new BigDecimal("0.00"));
	}

	public static Balance of(Money amount) {
		return new Balance(amount.decimalValue());
	}

	public Balance credit(Money amount) {
		return new Balance(this.amount.add(amount.decimalValue()));
	}

	public boolean canDebit(Money amount) {
		return this.amount.compareTo(amount.decimalValue()) >= 0;
	}

	public Balance debit(Money amount) {
		if (!canDebit(amount)) {
			throw new IllegalArgumentException("Balance cannot be negative");
		}
		return new Balance(this.amount.subtract(amount.decimalValue()));
	}

	static Balance fromDatabase(BigDecimal amount) {
		return new Balance(amount);
	}

	BigDecimal decimalValue() {
		return amount;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Balance balance && amount.equals(balance.amount);
	}

	@Override
	public int hashCode() {
		return amount.hashCode();
	}

	@Override
	public String toString() {
		return amount.toPlainString();
	}
}
