package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationResult;
import com.recargapay.wallet.domain.OperationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record MoneyOperationResponse(
		@Schema(description = "The operation this result belongs to; every ledger entry links to it") UUID operationId,
		@Schema(description = "The operation's type") OperationType type,
		@Schema(description = "The wallet the money moved in") UUID walletId,
		@Schema(description = "The amount that moved") Money amount,
		@Schema(description = "The wallet's balance after the operation") Balance balance,
		@Schema(description = "When the operation completed") Instant createdAt) {

	public static MoneyOperationResponse from(OperationResult result) {
		return new MoneyOperationResponse(result.operationId().value(), result.type(), result.walletId().value(),
				result.amount(), result.balance(), result.createdAt());
	}
}
