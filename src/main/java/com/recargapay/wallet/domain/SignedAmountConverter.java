package com.recargapay.wallet.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

@Converter
public class SignedAmountConverter implements AttributeConverter<SignedAmount, BigDecimal> {

	@Override
	public BigDecimal convertToDatabaseColumn(SignedAmount amount) {
		return amount.decimalValue();
	}

	@Override
	public SignedAmount convertToEntityAttribute(BigDecimal amount) {
		return SignedAmount.fromDatabase(amount);
	}
}
