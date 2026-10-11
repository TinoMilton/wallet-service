package com.recargapay.wallet.outbox.events;

import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationId;
import com.recargapay.wallet.domain.WalletId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A deposit credited one wallet. */
public record FundsDeposited(UUID operationId, UUID walletId, BigDecimal amount,
		Instant occurredAt) implements WalletEvent {

	public FundsDeposited(OperationId operationId, WalletId walletId, Money amount, Instant occurredAt) {
		this(operationId.value(), walletId.value(), amount.decimalValue(), occurredAt);
	}

	@Override
	public String eventType() {
		return "FundsDeposited";
	}
}
