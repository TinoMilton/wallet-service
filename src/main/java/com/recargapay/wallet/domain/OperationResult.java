package com.recargapay.wallet.domain;

import java.time.Instant;
import java.util.UUID;

public record OperationResult(UUID operationId, OperationType type, WalletId walletId, Money amount, Balance balance,
		Instant createdAt) {
}
