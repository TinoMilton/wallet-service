package com.recargapay.wallet.service;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.domain.exception.WalletAlreadyExistsException;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import com.recargapay.wallet.repository.WalletRepository;
import java.time.Clock;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Wallet creation and balance queries (techspec section 4). The balance on the
 * wallet row is the cache of the ledger; this service reads it, later tasks
 * will move money and keep it current.
 */
@Service
public class WalletService {

	/**
	 * The name Postgres gives the constraint that enforces one wallet per user
	 * (migration V1). Only a violation of this constraint means the wallet already
	 * exists; any other integrity failure is unexpected and travels on.
	 */
	private static final String ONE_WALLET_PER_USER_CONSTRAINT = "wallet_user_id_key";

	private final WalletRepository wallets;
	private final Clock clock;

	WalletService(WalletRepository wallets, Clock clock) {
		this.wallets = wallets;
		this.clock = clock;
	}

	public Wallet create(String userId) {
		Wallet wallet = new Wallet(new WalletId(UUID.randomUUID()), userId, clock.instant());
		try {
			wallets.saveAndFlush(wallet);
		} catch (DataIntegrityViolationException failure) {
			if (violatedOneWalletPerUser(failure)) {
				throw new WalletAlreadyExistsException(userId);
			}
			throw failure;
		}
		return wallet;
	}

	public Balance currentBalance(WalletId walletId) {
		return wallets.findById(walletId).map(Wallet::balance).orElseThrow(() -> new WalletNotFoundException(walletId));
	}

	private static boolean violatedOneWalletPerUser(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return ONE_WALLET_PER_USER_CONSTRAINT.equals(violation.getConstraintName());
			}
		}
		return false;
	}
}
