package com.recargapay.wallet.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransferRequest(@NotNull String fromWalletId, @NotNull String toWalletId, @NotNull BigDecimal amount) {
}
