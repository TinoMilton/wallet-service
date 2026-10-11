package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationResult;
import com.recargapay.wallet.domain.OperationType;
import java.time.Instant;
import java.util.UUID;

public record MoneyOperationResponse(UUID operationId, OperationType type, UUID walletId, Money amount, Balance balance,
		Instant createdAt) {

	public static MoneyOperationResponse from(OperationResult result) {
		return new MoneyOperationResponse(result.operationId(), result.type(), result.walletId().value(),
				result.amount(), result.balance(), result.createdAt());
	}
}
