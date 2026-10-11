package com.recargapay.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record MoneyRequest(
		@NotNull @Schema(description = "The amount to move, greater than zero, with at most two decimal places") BigDecimal amount) {
}
