package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import java.time.Instant;
import java.util.UUID;

/**
 * The 201 response of creating a wallet. The balance keeps its domain type;
 * Jackson renders it as a plain number with two decimals, like Money.
 */
public record CreateWalletResponse(UUID walletId, String userId, Balance balance, Instant createdAt) {
}
