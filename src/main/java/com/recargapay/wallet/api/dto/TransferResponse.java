package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationResult;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.WalletId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
		@Schema(description = "The operation this result belongs to; every ledger entry links to it") UUID operationId,
		@Schema(description = "The operation's type") OperationType type,
		@Schema(description = "The wallet the money left") UUID fromWalletId,
		@Schema(description = "The wallet the money reached") UUID toWalletId,
		@Schema(description = "The amount that moved") Money amount,
		@Schema(description = "The sender's balance after the transfer; only the sender's balance is returned, because the caller acts for the sender") Balance fromBalance,
		@Schema(description = "When the operation completed") Instant createdAt) {

	public static TransferResponse from(OperationResult result, WalletId to) {
		return new TransferResponse(result.operationId().value(), result.type(), result.walletId().value(), to.value(),
				result.amount(), result.balance(), result.createdAt());
	}
}
