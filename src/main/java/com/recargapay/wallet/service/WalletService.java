package com.recargapay.wallet.service;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.domain.exception.InvalidTimestampException;
import com.recargapay.wallet.domain.exception.WalletAlreadyExistsException;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import com.recargapay.wallet.repository.LedgerEntryRepository;
import com.recargapay.wallet.repository.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Wallet creation and balance queries (techspec section 4). The balance on the
 * wallet row is the cache of the ledger: this service reads it, and the money
 * operations in LedgerService keep it current.
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
	private final LedgerEntryRepository ledgerEntries;
	private final Clock clock;

	WalletService(WalletRepository wallets, LedgerEntryRepository ledgerEntries, Clock clock) {
		this.wallets = wallets;
		this.ledgerEntries = ledgerEntries;
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

	/**
	 * The balance of a wallet at a past instant (BR-13): the balance after the last
	 * ledger entry at or before it, with the instant itself inclusive. A wallet
	 * that existed but had no operations yet was holding zero (BR-14). Only the
	 * past can be asked about: the answer to a future instant could still change
	 * (BR-16), and before the wallet existed there was no wallet to ask about
	 * (BR-15).
	 */
	public Balance balanceAt(WalletId walletId, Instant at) {
		Instant cutoff = atMicrosecondPrecision(at);
		if (cutoff.isAfter(clock.instant())) {
			throw new InvalidTimestampException("the at parameter is in the future");
		}
		Wallet wallet = wallets.findById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
		if (cutoff.isBefore(wallet.createdAt())) {
			throw new WalletNotFoundException(walletId);
		}
		return ledgerEntries.lastEntryAtOrBefore(walletId, cutoff).map(LedgerEntry::balanceAfter)
				.orElseGet(Balance::zero);
	}

	/**
	 * PostgreSQL rounds a nanosecond instant to its microsecond timestamps: a
	 * cutoff one nanosecond below a stored microsecond would round up to it and
	 * wrongly include that entry (BR-13). Truncating first keeps the future check,
	 * the creation check and the ledger query all at the stored precision.
	 */
	private static Instant atMicrosecondPrecision(Instant at) {
		return at.truncatedTo(ChronoUnit.MICROS);
	}

	/** The wallet's ledger, newest first, one page at a time, for audit. */
	public Page<LedgerEntry> entries(WalletId walletId, Pageable pageable) {
		if (!wallets.existsById(walletId)) {
			throw new WalletNotFoundException(walletId);
		}
		return ledgerEntries.pageFor(walletId, pageable);
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
