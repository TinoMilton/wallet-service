package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.SignedAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of a wallet's ledger on the audit page: its position in the chain,
 * the operation that caused it, the signed delta it applied and the balance
 * after it.
 */
public record LedgerEntryResponse(@Schema(description = "Position in the wallet's ledger; 1 is the oldest") long seq,
		@Schema(description = "The operation that caused this entry") UUID operationId,
		@Schema(description = "The operation's type") OperationType type,
		@Schema(description = "The delta this entry applied; negative for money leaving the wallet") SignedAmount amountSigned,
		@Schema(description = "The wallet's balance after this entry") Balance balanceAfter,
		@Schema(description = "When the entry was recorded") Instant createdAt) {

	public static LedgerEntryResponse from(LedgerEntry entry) {
		return new LedgerEntryResponse(entry.sequence(), entry.operationId().value(), entry.operationType(),
				entry.amountSigned(), entry.balanceAfter(), entry.createdAt());
	}
}
