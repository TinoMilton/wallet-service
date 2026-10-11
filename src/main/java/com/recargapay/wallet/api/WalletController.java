package com.recargapay.wallet.api;

import com.recargapay.wallet.api.dto.CreateWalletRequest;
import com.recargapay.wallet.api.dto.CreateWalletResponse;
import com.recargapay.wallet.api.dto.CurrentBalanceResponse;
import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import com.recargapay.wallet.service.WalletService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

	private final WalletService walletService;
	private final Clock clock;

	WalletController(WalletService walletService, Clock clock) {
		this.walletService = walletService;
		this.clock = clock;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateWalletResponse create(@Valid @RequestBody CreateWalletRequest request) {
		Wallet wallet = walletService.create(request.userId());
		return new CreateWalletResponse(wallet.id().value(), wallet.userId(), wallet.balance(), wallet.createdAt());
	}

	@GetMapping("/{walletId}/balance")
	public CurrentBalanceResponse currentBalance(@PathVariable String walletId) {
		WalletId requestedWallet = readWalletId(walletId);
		Balance balance = walletService.currentBalance(requestedWallet);
		return new CurrentBalanceResponse(requestedWallet.value(), balance, clock.instant());
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
