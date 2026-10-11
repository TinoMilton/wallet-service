package com.recargapay.wallet.api;

import com.recargapay.wallet.api.dto.TransferRequest;
import com.recargapay.wallet.api.dto.TransferResponse;
import com.recargapay.wallet.domain.IdempotencyKey;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.domain.exception.InvalidAmountException;
import com.recargapay.wallet.domain.exception.InvalidIdempotencyKeyException;
import com.recargapay.wallet.domain.exception.MissingIdempotencyKeyException;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import com.recargapay.wallet.service.IdempotentCommandExecutor;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

	private final IdempotentCommandExecutor commands;

	TransferController(IdempotentCommandExecutor commands) {
		this.commands = commands;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TransferResponse transfer(@Valid @RequestBody TransferRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String rawKey) {
		WalletId from = readWalletId(request.fromWalletId());
		WalletId to = readWalletId(request.toWalletId());
		return TransferResponse.from(commands.transfer(from, to, readAmount(request), readKey(rawKey)), to);
	}

	private static WalletId readWalletId(String rawWalletId) {
		try {
			return new WalletId(UUID.fromString(rawWalletId));
		} catch (IllegalArgumentException invalidId) {
			throw new WalletNotFoundException(rawWalletId);
		}
	}

	private static Money readAmount(TransferRequest request) {
		try {
			return Money.of(request.amount().toPlainString());
		} catch (IllegalArgumentException invalidAmount) {
			throw new InvalidAmountException(invalidAmount);
		}
	}

	private static IdempotencyKey readKey(String rawKey) {
		if (rawKey == null || rawKey.isBlank()) {
			throw new MissingIdempotencyKeyException();
		}
		if (rawKey.length() > 128) {
			throw new InvalidIdempotencyKeyException();
		}
		return new IdempotencyKey(rawKey);
	}
}
