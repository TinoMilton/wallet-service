package com.recargapay.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The body of a create-wallet request. A user is an opaque string owned by
 * another system (techspec section 10, assumption 1); all this service checks
 * is that the caller sent one.
 */
public record CreateWalletRequest(@NotBlank(message = "userId must not be blank") String userId) {
}
