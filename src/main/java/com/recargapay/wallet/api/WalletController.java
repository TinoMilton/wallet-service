package com.recargapay.wallet.api;

import com.recargapay.wallet.api.dto.CreateWalletRequest;
import com.recargapay.wallet.api.dto.CreateWalletResponse;
import com.recargapay.wallet.api.dto.CurrentBalanceResponse;
import com.recargapay.wallet.service.WalletService;
import java.util.UUID;
import jakarta.validation.Valid;
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

	WalletController(WalletService walletService) {
		this.walletService = walletService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateWalletResponse create(@Valid @RequestBody CreateWalletRequest request) {
		return walletService.create(request.userId());
	}

	@GetMapping("/{walletId}/balance")
	public CurrentBalanceResponse currentBalance(@PathVariable UUID walletId) {
		return walletService.currentBalance(walletId);
	}
}
