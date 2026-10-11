package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The current balance of a wallet: the cached balance, with the instant the
 * reading reflects.
 */
public record CurrentBalanceResponse(@Schema(description = "The wallet the balance belongs to") UUID walletId,
		@Schema(description = "The balance at the instant below") Balance balance,
		@Schema(description = "The instant the reading reflects") Instant asOf) {
}
