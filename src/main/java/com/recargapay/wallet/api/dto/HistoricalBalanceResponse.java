package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The balance of a wallet at a past instant: the balance after the ledger's
 * last entry at or before the queried instant, which the response echoes as
 * {@code asOf}.
 */
public record HistoricalBalanceResponse(@Schema(description = "The wallet the balance belongs to") UUID walletId,
		@Schema(description = "The balance after the last entry at or before the queried instant") Balance balance,
		@Schema(description = "The instant that was asked for") Instant asOf) {
}
