package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.SignedAmount;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of a wallet's ledger on the audit page: its position in the chain,
 * the operation that caused it, the signed delta it applied and the balance
 * after it.
 */
public record LedgerEntryResponse(long seq, UUID operationId, OperationType type, SignedAmount amountSigned,
		Balance balanceAfter, Instant createdAt) {

	public static LedgerEntryResponse from(LedgerEntry entry) {
		return new LedgerEntryResponse(entry.sequence(), entry.operationId().value(), entry.operationType(),
				entry.amountSigned(), entry.balanceAfter(), entry.createdAt());
	}
}
