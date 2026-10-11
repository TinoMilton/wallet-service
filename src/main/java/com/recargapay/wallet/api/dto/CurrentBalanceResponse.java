package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import java.time.Instant;
import java.util.UUID;

/**
 * The current balance of a wallet: the cached balance, with the instant the
 * reading reflects.
 */
public record CurrentBalanceResponse(UUID walletId, Balance balance, Instant asOf) {
}
