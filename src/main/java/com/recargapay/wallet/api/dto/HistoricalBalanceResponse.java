package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import java.time.Instant;
import java.util.UUID;

/**
 * The balance of a wallet at a past instant: the balance after the ledger's
 * last entry at or before the queried instant, which the response echoes as
 * {@code asOf}.
 */
public record HistoricalBalanceResponse(UUID walletId, Balance balance, Instant asOf) {
}
