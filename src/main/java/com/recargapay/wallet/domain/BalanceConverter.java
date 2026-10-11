package com.recargapay.wallet.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

@Converter
public class BalanceConverter implements AttributeConverter<Balance, BigDecimal> {

	@Override
	public BigDecimal convertToDatabaseColumn(Balance balance) {
		return balance.decimalValue();
	}

	@Override
	public Balance convertToEntityAttribute(BigDecimal amount) {
		return Balance.fromDatabase(amount);
	}
}
