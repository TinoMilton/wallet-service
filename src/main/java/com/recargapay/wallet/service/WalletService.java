package com.recargapay.wallet.service;

import com.recargapay.wallet.api.dto.CreateWalletResponse;
import com.recargapay.wallet.api.dto.CurrentBalanceResponse;
import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.domain.exception.WalletAlreadyExistsException;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import com.recargapay.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Wallet creation and balance queries (techspec section 4). The balance on the
 * wallet row is the cache of the ledger; this service reads it, later tasks
 * will move money and keep it current.
 */
@Service
public class WalletService {

	/** BR-01: every wallet is created with a balance of zero, in its wire form. */
	private static final BigDecimal ZERO_BALANCE = new BigDecimal("0.00");

	private final WalletRepository wallets;
	private final Clock clock;

	WalletService(WalletRepository wallets, Clock clock) {
		this.wallets = wallets;
		this.clock = clock;
	}

	public CreateWalletResponse create(String userId) {
		UUID walletId = UUID.randomUUID();
		Instant createdAt = clock.instant();
		try {
			wallets.saveAndFlush(new Wallet(new WalletId(walletId), userId, createdAt));
		} catch (DataIntegrityViolationException duplicateUser) {
			// The unique constraint on user_id is the guard: a user has one wallet (BR-02).
			throw new WalletAlreadyExistsException(userId);
		}
		return new CreateWalletResponse(walletId, userId, ZERO_BALANCE, createdAt);
	}

	public CurrentBalanceResponse currentBalance(UUID walletId) {
		BigDecimal balance = wallets.findCurrentBalance(walletId)
				.orElseThrow(() -> new WalletNotFoundException(walletId));
		return new CurrentBalanceResponse(walletId, balance, clock.instant());
	}
}
