package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Balance;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The 201 response of creating a wallet. The balance keeps its domain type;
 * Jackson renders it as a plain number with two decimals, like Money.
 */
public record CreateWalletResponse(@Schema(description = "The created wallet's id") UUID walletId,
		@Schema(description = "The user the wallet belongs to") String userId,
		@Schema(description = "Zero: a new wallet starts empty") Balance balance,
		@Schema(description = "When the wallet was created") Instant createdAt) {
}
