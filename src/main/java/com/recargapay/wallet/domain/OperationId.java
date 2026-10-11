package com.recargapay.wallet.domain;

import java.util.Objects;
import java.util.UUID;

public record OperationId(UUID value) {
	public OperationId {
		Objects.requireNonNull(value, "operation id");
	}
}
