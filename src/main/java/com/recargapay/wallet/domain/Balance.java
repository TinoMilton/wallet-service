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

	BigDecimal decimalValue() {
		return amount;
	}
}
