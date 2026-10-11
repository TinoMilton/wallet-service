package com.recargapay.wallet.domain;

import java.time.Instant;

public record OperationResult(OperationId operationId, OperationType type, WalletId walletId, Money amount,
		Balance balance, Instant createdAt) {
}
