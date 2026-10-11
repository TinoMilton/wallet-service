package com.recargapay.wallet.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The 201 response of creating a wallet. The balance appears in its wire form,
 * a plain number with two decimals; the domain keeps its own {@code Balance}
 * type (techspec section 6).
 */
public record CreateWalletResponse(UUID walletId, String userId, BigDecimal balance, Instant createdAt) {
}
