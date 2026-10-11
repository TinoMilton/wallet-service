package com.recargapay.wallet.domain;

import java.math.BigDecimal;

/**
 * A nonzero ledger delta. Money remains strictly positive at the API boundary.
 */
public final class SignedAmount {

	private final BigDecimal amount;

	private SignedAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public static SignedAmount credit(Money amount) {
		return new SignedAmount(amount.decimalValue());
	}

	public static SignedAmount debit(Money amount) {
		return new SignedAmount(amount.decimalValue().negate());
	}

	static SignedAmount fromDatabase(BigDecimal amount) {
		return new SignedAmount(amount);
	}

	BigDecimal decimalValue() {
		return amount;
	}
}
