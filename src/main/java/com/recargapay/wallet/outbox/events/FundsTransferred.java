package com.recargapay.wallet.outbox.events;

import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationId;
import com.recargapay.wallet.domain.WalletId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A transfer debited the sender and credited the receiver. */
public record FundsTransferred(UUID operationId, UUID fromWalletId, UUID toWalletId, BigDecimal amount,
		Instant occurredAt) implements WalletEvent {

	public FundsTransferred(OperationId operationId, WalletId fromWalletId, WalletId toWalletId, Money amount,
			Instant occurredAt) {
		this(operationId.value(), fromWalletId.value(), toWalletId.value(), amount.decimalValue(), occurredAt);
	}

	@Override
	public String eventType() {
		return "FundsTransferred";
	}
}
