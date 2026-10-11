package com.recargapay.wallet.service;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.IdempotencyKey;
import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.Operation;
import com.recargapay.wallet.domain.OperationId;
import com.recargapay.wallet.domain.OperationResult;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.OutboxEvent;
import com.recargapay.wallet.domain.RequestFingerprint;
import com.recargapay.wallet.domain.SignedAmount;
import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.domain.exception.InsufficientFundsException;
import com.recargapay.wallet.domain.exception.IdempotencyKeyReusedException;
import com.recargapay.wallet.domain.exception.SameWalletTransferException;
import com.recargapay.wallet.domain.exception.WalletBusyException;
import com.recargapay.wallet.domain.exception.WalletNotFoundException;
import com.recargapay.wallet.outbox.EventPayloads;
import com.recargapay.wallet.outbox.events.FundsDeposited;
import com.recargapay.wallet.outbox.events.FundsTransferred;
import com.recargapay.wallet.outbox.events.FundsWithdrawn;
import com.recargapay.wallet.outbox.events.WalletEvent;
import com.recargapay.wallet.repository.LedgerEntryRepository;
import com.recargapay.wallet.repository.OperationRepository;
import com.recargapay.wallet.repository.OutboxEventRepository;
import com.recargapay.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
	private final EventPayloads payloads;
	private final RequestFingerprints fingerprints;
	private final Clock clock;

	LedgerService(EntityManager entityManager, WalletRepository wallets, OperationRepository operations,
			LedgerEntryRepository entries, OutboxEventRepository events, EventPayloads payloads,
			RequestFingerprints fingerprints, Clock clock) {
		this.entityManager = entityManager;
		this.wallets = wallets;
		this.operations = operations;
		this.entries = entries;
		this.events = events;
		this.payloads = payloads;
		this.fingerprints = fingerprints;
		this.clock = clock;
	}

	@Transactional
	public OperationResult deposit(WalletId walletId, Money amount, IdempotencyKey key) {
		Operation operation = recordOperation(OperationType.DEPOSIT, walletId, amount, key);
		setLockTimeout();
		Wallet wallet = lockWallet(walletId);
		LedgerPosition position = nextLedgerPosition(wallet.id());
		Balance balanceAfter = wallet.balance().credit(amount);
		appendEntry(wallet, operation, SignedAmount.credit(amount), balanceAfter, position);
		updateBalance(wallet, balanceAfter);
		recordEvent(operation,
				new FundsDeposited(new OperationId(operation.id()), walletId, amount, position.recordedAt()));
		return new OperationResult(new OperationId(operation.id()), OperationType.DEPOSIT, walletId, amount,
				balanceAfter, position.recordedAt());
	}

	@Transactional
	public OperationResult withdraw(WalletId walletId, Money amount, IdempotencyKey key) {
		Operation operation = recordOperation(OperationType.WITHDRAWAL, walletId, amount, key);
		setLockTimeout();
		Wallet wallet = lockWallet(walletId);
		LedgerPosition position = nextLedgerPosition(wallet.id());
		checkFunds(wallet, amount);
		Balance balanceAfter = wallet.balance().debit(amount);
		appendEntry(wallet, operation, SignedAmount.debit(amount), balanceAfter, position);
		updateBalance(wallet, balanceAfter);
		recordEvent(operation,
				new FundsWithdrawn(new OperationId(operation.id()), walletId, amount, position.recordedAt()));
		return new OperationResult(new OperationId(operation.id()), OperationType.WITHDRAWAL, walletId, amount,
				balanceAfter, position.recordedAt());
	}

	@Transactional
	public OperationResult transfer(WalletId from, WalletId to, Money amount, IdempotencyKey key) {
		Operation operation = recordTransferOperation(from, to, amount, key);
		rejectSameWallet(from, to);
		setLockTimeout();
		WalletPair pair = lockWalletPair(from, to);
		Wallet sender = pair.sender();
		Wallet receiver = pair.receiver();
		checkFunds(sender, amount);
		LedgerPosition senderPosition = nextLedgerPosition(from);
		LedgerPosition receiverPosition = nextLedgerPosition(to);
		Instant recordedAt = latestOf(senderPosition.recordedAt(), receiverPosition.recordedAt());
		Balance senderBalance = sender.balance().debit(amount);
		Balance receiverBalance = receiver.balance().credit(amount);
		appendTransferEntries(pair, operation, amount, senderBalance, receiverBalance, senderPosition, receiverPosition,
				recordedAt);
		updateTransferBalances(pair, senderBalance, receiverBalance);
		recordEvent(operation, new FundsTransferred(new OperationId(operation.id()), from, to, amount, recordedAt));
		return new OperationResult(new OperationId(operation.id()), OperationType.TRANSFER, from, amount, senderBalance,
				recordedAt);
	}

	@Transactional(readOnly = true)
	public OperationResult replay(IdempotencyKey key, RequestFingerprint fingerprint) {
		Operation operation = operations.findByIdempotencyKey(key)
				.orElseThrow(() -> new IllegalStateException("Committed operation missing for idempotency key"));
		checkFingerprint(operation, fingerprint);
		List<LedgerEntry> originalEntries = entries.forOperation(operation.id());
		LedgerEntry resultEntry = resultEntry(operation, originalEntries);
		return new OperationResult(new OperationId(operation.id()), operation.type(), resultEntry.walletId(),
				resultEntry.amountSigned().magnitude(), resultEntry.balanceAfter(), resultEntry.createdAt());
	}

	private void checkFingerprint(Operation operation, RequestFingerprint fingerprint) {
		if (!operation.requestFingerprint().equals(fingerprint)) {
			throw new IdempotencyKeyReusedException();
		}
	}

	private LedgerEntry resultEntry(Operation operation, List<LedgerEntry> originalEntries) {
		if (operation.type() == OperationType.TRANSFER) {
			return originalEntries.stream().filter(entry -> entry.amountSigned().isDebit()).findFirst()
					.orElseThrow(() -> new IllegalStateException("Transfer debit entry missing"));
		}
		return originalEntries.getFirst();
	}

	private void rejectSameWallet(WalletId from, WalletId to) {
		if (from.equals(to)) {
			throw new SameWalletTransferException();
		}
	}

	private WalletPair lockWalletPair(WalletId from, WalletId to) {
		if (from.value().toString().compareTo(to.value().toString()) < 0) {
			Wallet sender = lockWallet(from);
			Wallet receiver = lockWallet(to);
			return new WalletPair(sender, receiver);
		}
		Wallet receiver = lockWallet(to);
		Wallet sender = lockWallet(from);
		return new WalletPair(sender, receiver);
	}

	private Instant latestOf(Instant first, Instant second) {
		return first.isAfter(second) ? first : second;
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

	private LedgerPosition nextLedgerPosition(WalletId walletId) {
		Optional<LedgerEntry> latest = entries.latestFor(walletId);
		long sequence = latest.map(entry -> entry.sequence() + 1).orElse(1L);
		return new LedgerPosition(sequence, recordedAtInSequenceOrder(latest));
	}

	private Instant recordedAtInSequenceOrder(Optional<LedgerEntry> latest) {
		Instant now = clock.instant();
		return latest.map(last -> {
			// PostgreSQL stores microseconds: even with an unchanged clock, each
			// later sequence must have a later timestamp.
			Instant afterLast = last.createdAt().plusNanos(1_000);
			return now.isAfter(afterLast) ? now : afterLast;
		}).orElse(now);
	}

	private Operation recordOperation(OperationType type, WalletId walletId, Money amount, IdempotencyKey key) {
		return saveOperation(type, key, fingerprints.forRequest(type, walletId, amount));
	}

	private Operation recordTransferOperation(WalletId from, WalletId to, Money amount, IdempotencyKey key) {
		RequestFingerprint fingerprint = fingerprints.forRequest(OperationType.TRANSFER, from, to, amount);
		return saveOperation(OperationType.TRANSFER, key, fingerprint);
	}

	private Operation saveOperation(OperationType type, IdempotencyKey key, RequestFingerprint fingerprint) {
		// Flush the unique key before locking wallets: a competing request can only
		// wait on this operation, never hold a wallet lock while it waits.
		return operations.saveAndFlush(new Operation(UUID.randomUUID(), type, key, fingerprint, clock.instant()));
	}

	private void checkFunds(Wallet wallet, Money amount) {
		if (!wallet.balance().canDebit(amount)) {
			throw new InsufficientFundsException();
		}
	}

	private void appendEntry(Wallet wallet, Operation operation, SignedAmount amount, Balance balanceAfter,
			LedgerPosition position) {
		entries.save(LedgerEntry.record(UUID.randomUUID(), operation, wallet, position.sequence(), amount, balanceAfter,
				position.recordedAt()));
	}

	private void appendTransferEntries(WalletPair pair, Operation operation, Money amount, Balance senderBalance,
			Balance receiverBalance, LedgerPosition senderPosition, LedgerPosition receiverPosition,
			Instant recordedAt) {
		appendEntry(pair.sender(), operation, SignedAmount.debit(amount), senderBalance,
				new LedgerPosition(senderPosition.sequence(), recordedAt));
		appendEntry(pair.receiver(), operation, SignedAmount.credit(amount), receiverBalance,
				new LedgerPosition(receiverPosition.sequence(), recordedAt));
	}

	private void updateBalance(Wallet wallet, Balance balanceAfter) {
		wallet.recordBalance(balanceAfter);
	}

	private void updateTransferBalances(WalletPair pair, Balance senderBalance, Balance receiverBalance) {
		updateBalance(pair.sender(), senderBalance);
		updateBalance(pair.receiver(), receiverBalance);
	}

	private void recordEvent(Operation operation, WalletEvent event) {
		// Invariant 4: one event per successful operation, written with
		// the entries in the same transaction. A rejected operation never
		// gets here; a replay only reads.
		events.save(OutboxEvent.forOperation(new OperationId(operation.id()), event.eventType(), payloads.asJson(event),
				event.occurredAt()));
	}

	private record LedgerPosition(long sequence, Instant recordedAt) {
	}

	private record WalletPair(Wallet sender, Wallet receiver) {
	}
}
