package com.recargapay.wallet.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The current balance of a wallet: the cached balance, with the instant the
 * reading reflects.
 */
public record CurrentBalanceResponse(UUID walletId, BigDecimal balance, Instant asOf) {
}
