package com.recargapay.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of a create-wallet request. A user is an opaque string owned by
 * another system (techspec section 10, assumption 1); all this service checks
 * is that the caller sent one, and that it fits the wallet table's column.
 */
public record CreateWalletRequest(
		@NotBlank(message = "userId must not be blank") @Size(max = 64, message = "userId must be at most 64 characters") @Schema(description = "The user the wallet belongs to, opaque, owned by another system") String userId) {
}
