package com.recargapay.wallet.domain;

import com.fasterxml.jackson.annotation.JsonValue;
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

	/**
	 * The delta as a plain decimal. Carries {@code @JsonValue} so the audit page
	 * can show the signed delta as a JSON number, the way Balance serialises.
	 */
	@JsonValue
	public BigDecimal decimalValue() {
		return amount;
	}
}
