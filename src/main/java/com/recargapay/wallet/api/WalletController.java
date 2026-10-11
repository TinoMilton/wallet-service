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
import com.recargapay.wallet.domain.exception.InvalidPageRequestException;
import com.recargapay.wallet.domain.exception.InvalidTimestampException;
import com.recargapay.wallet.domain.exception.MissingIdempotencyKeyException;
import com.recargapay.wallet.service.IdempotentCommandExecutor;
import com.recargapay.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@Tag(name = "Wallets", description = "Wallet creation, balances, the audit ledger and money movement in one wallet")
public class WalletController {

	/**
	 * The largest page the audit endpoint returns. A caller asking for more is
	 * served the maximum, and the applied size is echoed in the response, so a
	 * caller that pages until the end still sees every entry.
	 */
	private static final int MAXIMUM_PAGE_SIZE = 100;

	/** The page size the audit endpoint returns when the caller asks for none. */
	private static final int DEFAULT_PAGE_SIZE = 20;

	private final WalletService walletService;
	private final IdempotentCommandExecutor commands;
	private final Clock clock;

	WalletController(WalletService walletService, IdempotentCommandExecutor commands, Clock clock) {
		this.walletService = walletService;
		this.commands = commands;
		this.clock = clock;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a wallet for a user")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "The wallet was created", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidWalletBody"),
			@ApiResponse(responseCode = "409", ref = "#/components/responses/WalletAlreadyExists")})
	public CreateWalletResponse create(@Valid @RequestBody CreateWalletRequest request) {
		Wallet wallet = walletService.create(request.userId());
		return new CreateWalletResponse(wallet.id().value(), wallet.userId(), wallet.balance(), wallet.createdAt());
	}

	@GetMapping(value = "/{walletId}/balance", params = "!at")
	@Operation(summary = "Read the balance of a wallet, now or at a past instant")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The balance at the requested instant", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidTimestamp"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/WalletNotFound")})
	public CurrentBalanceResponse currentBalance(@PathVariable String walletId) {
		WalletId requestedWallet = readWalletId(walletId);
		Balance balance = walletService.currentBalance(requestedWallet);
		return new CurrentBalanceResponse(requestedWallet.value(), balance, clock.instant());
	}

	@GetMapping(value = "/{walletId}/balance", params = "at")
	@Operation(summary = "Read the balance of a wallet, now or at a past instant")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The balance at the requested instant", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidTimestamp"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/WalletNotFound")})
	public HistoricalBalanceResponse balanceAt(@PathVariable String walletId,
			@Parameter(description = "The instant to read the balance at, ISO-8601 with an offset; omitted means now") @RequestParam("at") String rawAt) {
		WalletId requestedWallet = readWalletId(walletId);
		Instant at = readTimestamp(rawAt);
		Balance balance = walletService.balanceAt(requestedWallet, at);
		return new HistoricalBalanceResponse(requestedWallet.value(), balance, at);
	}

	@GetMapping("/{walletId}/entries")
	@Operation(summary = "Read a wallet's ledger, newest first")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "One page of the ledger", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/WalletNotFound")})
	public LedgerEntriesResponse entries(@PathVariable String walletId,
			@Parameter(description = "The page to return, numbered from 0") @RequestParam(name = "page", required = false) String rawPage,
			@Parameter(description = "Entries per page, at most 100") @RequestParam(name = "size", required = false) String rawSize) {
		WalletId requestedWallet = readWalletId(walletId);
		Pageable paging = readPaging(rawPage, rawSize);
		Page<LedgerEntry> ledger = walletService.entries(requestedWallet, paging);
		return LedgerEntriesResponse.from(requestedWallet, ledger);
	}

	@PostMapping("/{walletId}/deposits")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Deposit into a wallet")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "The deposit was applied", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidMoneyRequest"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/WalletNotFound"),
			@ApiResponse(responseCode = "422", ref = "#/components/responses/RejectedOperation"),
			@ApiResponse(responseCode = "503", ref = "#/components/responses/WalletBusy")})
	public MoneyOperationResponse deposit(@PathVariable String walletId, @Valid @RequestBody MoneyRequest request,
			@Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Client-generated key; repeating a request with the same key returns the original result") @RequestHeader(name = "Idempotency-Key", required = false) String rawKey) {
		return MoneyOperationResponse
				.from(commands.deposit(readWalletId(walletId), readAmount(request), readIdempotencyKey(rawKey)));
	}

	@PostMapping("/{walletId}/withdrawals")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Withdraw from a wallet")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "The withdrawal was applied", useReturnTypeSchema = true),
			@ApiResponse(responseCode = "400", ref = "#/components/responses/InvalidMoneyRequest"),
			@ApiResponse(responseCode = "404", ref = "#/components/responses/WalletNotFound"),
			@ApiResponse(responseCode = "422", ref = "#/components/responses/RejectedOperation"),
			@ApiResponse(responseCode = "503", ref = "#/components/responses/WalletBusy")})
	public MoneyOperationResponse withdraw(@PathVariable String walletId, @Valid @RequestBody MoneyRequest request,
			@Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Client-generated key; repeating a request with the same key returns the original result") @RequestHeader(name = "Idempotency-Key", required = false) String rawKey) {
		return MoneyOperationResponse
				.from(commands.withdraw(readWalletId(walletId), readAmount(request), readIdempotencyKey(rawKey)));
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

	/**
	 * The paging parameters are read as they arrive so that a value the endpoint
	 * cannot honour, unparsable or out of range, is a bad request rather than a
	 * binding failure surfacing as a server error.
	 */
	private static Pageable readPaging(String rawPage, String rawSize) {
		int page = rawPage == null ? 0 : thePagingNumber("page", rawPage);
		int size = rawSize == null ? DEFAULT_PAGE_SIZE : thePagingNumber("size", rawSize);
		if (page < 0 || size < 1) {
			throw new InvalidPageRequestException("page must be 0 or more and size must be 1 or more");
		}
		return PageRequest.of(page, cappedPageSize(size));
	}

	/** One paging parameter as a number; an unparsable value is a bad request. */
	private static int thePagingNumber(String name, String rawValue) {
		try {
			return Integer.parseInt(rawValue);
		} catch (NumberFormatException notANumber) {
			throw new InvalidPageRequestException("the " + name + " parameter must be a number");
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
