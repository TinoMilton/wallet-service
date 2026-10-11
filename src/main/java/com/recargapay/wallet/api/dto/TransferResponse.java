package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationResult;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.WalletId;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID operationId, OperationType type, UUID fromWalletId, UUID toWalletId, Money amount,
		Balance fromBalance, Instant createdAt) {

	public static TransferResponse from(OperationResult result, WalletId to) {
		return new TransferResponse(result.operationId().value(), result.type(), result.walletId().value(), to.value(),
				result.amount(), result.balance(), result.createdAt());
	}
}
