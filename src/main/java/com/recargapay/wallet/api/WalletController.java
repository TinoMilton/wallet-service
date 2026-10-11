package com.recargapay.wallet.api;

import com.recargapay.wallet.api.dto.CreateWalletRequest;
import com.recargapay.wallet.api.dto.CreateWalletResponse;
import com.recargapay.wallet.api.dto.CurrentBalanceResponse;
import com.recargapay.wallet.api.dto.HistoricalBalanceResponse;
import com.recargapay.wallet.api.dto.LedgerEntriesResponse;
import com.recargapay.wallet.api.dto.MoneyOperationResponse;
import com.recargapay.wallet.api.dto.MoneyRequest;
import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.IdempotencyKey;
import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import com.recargapay.wallet.domain.exception.InvalidAmountException;
import com.recargapay.wallet.domain.exception.InvalidIdempotencyKeyException;
import com.recargapay.wallet.domain.exception.InvalidTimestampException;
import com.recargapay.wallet.domain.exception.MissingIdempotencyKeyException;
import com.recargapay.wallet.service.LedgerService;
import com.recargapay.wallet.service.WalletService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

	/**
	 * The largest page the audit endpoint returns. A caller asking for more is
	 * served the maximum, and the applied size is echoed in the response, so a
	 * caller that pages until the end still sees every entry.
	 */
	private static final int MAXIMUM_PAGE_SIZE = 100;

	private final WalletService walletService;
	private final LedgerService ledgerService;
	private final Clock clock;

	WalletController(WalletService walletService, LedgerService ledgerService, Clock clock) {
		this.walletService = walletService;
		this.ledgerService = ledgerService;
		this.clock = clock;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateWalletResponse create(@Valid @RequestBody CreateWalletRequest request) {
		Wallet wallet = walletService.create(request.userId());
		return new CreateWalletResponse(wallet.id().value(), wallet.userId(), wallet.balance(), wallet.createdAt());
	}

	@GetMapping(value = "/{walletId}/balance", params = "!at")
	public CurrentBalanceResponse currentBalance(@PathVariable String walletId) {
		WalletId requestedWallet = readWalletId(walletId);
		Balance balance = walletService.currentBalance(requestedWallet);
		return new CurrentBalanceResponse(requestedWallet.value(), balance, clock.instant());
	}

	@GetMapping(value = "/{walletId}/balance", params = "at")
	public HistoricalBalanceResponse balanceAt(@PathVariable String walletId, @RequestParam("at") String rawAt) {
		WalletId requestedWallet = readWalletId(walletId);
		Instant at = readTimestamp(rawAt);
		Balance balance = walletService.balanceAt(requestedWallet, at);
		return new HistoricalBalanceResponse(requestedWallet.value(), balance, at);
	}

	@GetMapping("/{walletId}/entries")
	public LedgerEntriesResponse entries(@PathVariable String walletId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		WalletId requestedWallet = readWalletId(walletId);
		Page<LedgerEntry> ledger = walletService.entries(requestedWallet, PageRequest.of(page, cappedPageSize(size)));
		return LedgerEntriesResponse.from(requestedWallet, ledger);
	}

	@PostMapping("/{walletId}/deposits")
	@ResponseStatus(HttpStatus.CREATED)
	public MoneyOperationResponse deposit(@PathVariable String walletId, @Valid @RequestBody MoneyRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String rawKey) {
		return MoneyOperationResponse
				.from(ledgerService.deposit(readWalletId(walletId), readAmount(request), readIdempotencyKey(rawKey)));
	}

	@PostMapping("/{walletId}/withdrawals")
	@ResponseStatus(HttpStatus.CREATED)
	public MoneyOperationResponse withdraw(@PathVariable String walletId, @Valid @RequestBody MoneyRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) String rawKey) {
		return MoneyOperationResponse
				.from(ledgerService.withdraw(readWalletId(walletId), readAmount(request), readIdempotencyKey(rawKey)));
	}

	private static Money readAmount(MoneyRequest request) {
		try {
			return Money.of(request.amount().toPlainString());
		} catch (IllegalArgumentException invalidAmount) {
			throw new InvalidAmountException(invalidAmount);
		}
	}

	/**
	 * BR-16: the instant must be ISO-8601 with an explicit offset. Whatever the
	 * parser rejects, malformed or offset-less, is an invalid timestamp.
	 */
	private static Instant readTimestamp(String rawAt) {
		try {
			return Instant.parse(rawAt);
		} catch (DateTimeParseException invalidTimestamp) {
			throw new InvalidTimestampException("the at parameter must be an ISO-8601 instant with an offset");
		}
	}

	private static int cappedPageSize(int requestedSize) {
		return Math.min(requestedSize, MAXIMUM_PAGE_SIZE);
	}

	private static IdempotencyKey readIdempotencyKey(String rawKey) {
		if (rawKey == null || rawKey.isBlank()) {
			throw new MissingIdempotencyKeyException();
		}
		if (rawKey.length() > 128) {
			throw new InvalidIdempotencyKeyException();
		}
		return new IdempotencyKey(rawKey);
	}

	/**
	 * A wallet id in the URL that is not a valid UUID is treated as an unknown
	 * wallet (BR-11), so this conversion failure reports exactly that.
	 */
	private static WalletId readWalletId(String rawWalletId) {
		try {
			return new WalletId(UUID.fromString(rawWalletId));
		} catch (IllegalArgumentException notAUuid) {
			throw new WalletNotFoundException(rawWalletId);
		}
	}
}
