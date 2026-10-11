package com.recargapay.wallet.api.dto;

import com.recargapay.wallet.domain.Money;
import jakarta.validation.constraints.NotNull;

public record MoneyRequest(@NotNull Money amount) {
}
