package com.recargapay.wallet.service;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.IdempotencyKey;
import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.Operation;
import com.recargapay.wallet.domain.OperationResult;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.OutboxEvent;
import com.recargapay.wallet.domain.SignedAmount;
import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.domain.exception.InsufficientFundsException;
import com.recargapay.wallet.domain.exception.WalletBusyException;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import com.recargapay.wallet.repository.LedgerEntryRepository;
import com.recargapay.wallet.repository.OperationRepository;
import com.recargapay.wallet.repository.OutboxEventRepository;
import com.recargapay.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

	private final EntityManager entityManager;
	private final WalletRepository wallets;
	private final OperationRepository operations;
	private final LedgerEntryRepository entries;
	private final OutboxEventRepository events;
	private final RequestFingerprint fingerprints;
	private final Clock clock;

	LedgerService(EntityManager entityManager, WalletRepository wallets, OperationRepository operations,
			LedgerEntryRepository entries, OutboxEventRepository events, RequestFingerprint fingerprints, Clock clock) {
		this.entityManager = entityManager;
		this.wallets = wallets;
		this.operations = operations;
		this.entries = entries;
		this.events = events;
		this.fingerprints = fingerprints;
		this.clock = clock;
	}

	@Transactional
	public OperationResult deposit(WalletId walletId, Money amount, IdempotencyKey key) {
		setLockTimeout();
		Wallet wallet = lockWallet(walletId);
		Instant recordedAt = nextRecordedAt(walletId);
		Operation operation = recordOperation(OperationType.DEPOSIT, walletId, amount, key, recordedAt);
		Balance balanceAfter = wallet.balance().credit(amount);
		appendEntry(wallet, operation, SignedAmount.credit(amount), balanceAfter, recordedAt);
		updateBalance(wallet, balanceAfter);
		recordEvent(operation, "FundsDeposited", recordedAt);
		return new OperationResult(operation.id(), OperationType.DEPOSIT, walletId, amount, balanceAfter, recordedAt);
	}

	@Transactional
	public OperationResult withdraw(WalletId walletId, Money amount, IdempotencyKey key) {
		setLockTimeout();
		Wallet wallet = lockWallet(walletId);
		Instant recordedAt = nextRecordedAt(walletId);
		Operation operation = recordOperation(OperationType.WITHDRAWAL, walletId, amount, key, recordedAt);
		checkFunds(wallet, amount);
		Balance balanceAfter = wallet.balance().debit(amount);
		appendEntry(wallet, operation, SignedAmount.debit(amount), balanceAfter, recordedAt);
		updateBalance(wallet, balanceAfter);
		recordEvent(operation, "FundsWithdrawn", recordedAt);
		return new OperationResult(operation.id(), OperationType.WITHDRAWAL, walletId, amount, balanceAfter,
				recordedAt);
	}

	private void setLockTimeout() {
		// PostgreSQL ignores a positive JPA lock-timeout hint; SET LOCAL scopes the
		// actual wait limit to this transaction.
		entityManager.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();
	}

	private Wallet lockWallet(WalletId walletId) {
		try {
			return wallets.lockById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
		} catch (RuntimeException failure) {
			if (isPostgresLockTimeout(failure)) {
				throw new WalletBusyException(failure);
			}
			throw failure;
		}
	}

	private boolean isPostgresLockTimeout(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
				return true;
			}
		}
		return false;
	}

	private Instant nextRecordedAt(WalletId walletId) {
		Instant now = clock.instant();
		return entries.latestFor(walletId).map(last -> {
			Instant afterLast = last.createdAt().plusNanos(1_000);
			return now.isAfter(afterLast) ? now : afterLast;
		}).orElse(now);
	}

	private Operation recordOperation(OperationType type, WalletId walletId, Money amount, IdempotencyKey key,
			Instant recordedAt) {
		Operation operation = new Operation(UUID.randomUUID(), type, key,
				fingerprints.forRequest(type, walletId, amount), recordedAt);
		return operations.save(operation);
	}

	private void checkFunds(Wallet wallet, Money amount) {
		if (!wallet.balance().canDebit(amount)) {
			throw new InsufficientFundsException();
		}
	}

	private void appendEntry(Wallet wallet, Operation operation, SignedAmount amount, Balance balanceAfter,
			Instant recordedAt) {
		long sequence = entries.latestFor(wallet.id()).map(last -> last.sequence() + 1).orElse(1L);
		entries.save(
				LedgerEntry.record(UUID.randomUUID(), operation, wallet, sequence, amount, balanceAfter, recordedAt));
	}

	private void updateBalance(Wallet wallet, Balance balanceAfter) {
		wallet.recordBalance(balanceAfter);
	}

	private void recordEvent(Operation operation, String eventType, Instant recordedAt) {
		String payload = "{\"operationId\":\"" + operation.id() + "\"}";
		events.save(new OutboxEvent(UUID.randomUUID(), operation.id(), eventType, payload, recordedAt));
	}
}
