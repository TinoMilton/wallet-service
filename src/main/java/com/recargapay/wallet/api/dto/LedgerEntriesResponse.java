package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.WalletId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/**
 * One page of a wallet's ledger, newest first: the chain the ledger keeps,
 * exposed for audit. The {@code size} the response reports is the size the
 * server applied.
 */
public record LedgerEntriesResponse(UUID walletId, int page, int size, long totalEntries,
		List<LedgerEntryResponse> entries) {

	public static LedgerEntriesResponse from(WalletId walletId, Page<LedgerEntry> ledger) {
		List<LedgerEntryResponse> entries = ledger.getContent().stream().map(LedgerEntryResponse::from).toList();
		return new LedgerEntriesResponse(walletId.value(), ledger.getNumber(), ledger.getSize(),
				ledger.getTotalElements(), entries);
	}
}
