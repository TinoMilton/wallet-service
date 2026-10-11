package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.WalletId;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/**
 * One page of a wallet's ledger, newest first: the chain the ledger keeps,
 * exposed for audit. The {@code size} the response reports is the size the
 * server applied.
 */
public record LedgerEntriesResponse(@Schema(description = "The wallet whose ledger this page belongs to") UUID walletId,
		@Schema(description = "The page the server returned, numbered from 0") int page,
		@Schema(description = "The page size the server applied, at most 100") int size,
		@Schema(description = "How many entries the wallet's ledger holds") long totalEntries,
		@Schema(description = "One page of the ledger, newest first") List<LedgerEntryResponse> entries) {

	public static LedgerEntriesResponse from(WalletId walletId, Page<LedgerEntry> ledger) {
		List<LedgerEntryResponse> entries = ledger.getContent().stream().map(LedgerEntryResponse::from).toList();
		return new LedgerEntriesResponse(walletId.value(), ledger.getNumber(), ledger.getSize(),
				ledger.getTotalElements(), entries);
	}
}
