package com.recargapay.wallet.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A strictly positive BRL amount. Balances and signed ledger deltas have
 * different invariants.
 */
public final class Money implements Comparable<Money> {

	private final BigDecimal amount;

	private Money(BigDecimal amount) {
		Objects.requireNonNull(amount, "amount");
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("Amount must be positive");
		}
		if (amount.scale() > 2) {
			throw new IllegalArgumentException("Amount must have at most two decimal places");
		}
		this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
	}

	public static Money of(String amount) {
		return new Money(new BigDecimal(amount));
	}

	BigDecimal decimalValue() {
		return amount;
	}

	@Override
	public int compareTo(Money other) {
		return amount.compareTo(other.amount);
	}

	@Override
	public String toString() {
		return amount.toPlainString();
	}
}
