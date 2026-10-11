package com.recargapay.wallet.service;

import com.recargapay.wallet.domain.IdempotencyKey;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationResult;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.RequestFingerprint;
import com.recargapay.wallet.domain.WalletId;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class IdempotentCommandExecutor {

	private static final String UNIQUE_KEY_CONSTRAINT = "operation_idempotency_key_key";

	private final LedgerService ledger;
	private final RequestFingerprints fingerprints;

	IdempotentCommandExecutor(LedgerService ledger, RequestFingerprints fingerprints) {
		this.ledger = ledger;
		this.fingerprints = fingerprints;
	}

	public OperationResult deposit(WalletId wallet, Money amount, IdempotencyKey key) {
		RequestFingerprint fingerprint = fingerprints.forRequest(OperationType.DEPOSIT, wallet, amount);
		return execute(key, fingerprint, () -> ledger.deposit(wallet, amount, key));
	}

	public OperationResult withdraw(WalletId wallet, Money amount, IdempotencyKey key) {
		RequestFingerprint fingerprint = fingerprints.forRequest(OperationType.WITHDRAWAL, wallet, amount);
		return execute(key, fingerprint, () -> ledger.withdraw(wallet, amount, key));
	}

	public OperationResult transfer(WalletId from, WalletId to, Money amount, IdempotencyKey key) {
		RequestFingerprint fingerprint = fingerprints.forRequest(OperationType.TRANSFER, from, to, amount);
		return execute(key, fingerprint, () -> ledger.transfer(from, to, amount, key));
	}

	private OperationResult execute(IdempotencyKey key, RequestFingerprint fingerprint,
			Supplier<OperationResult> command) {
		try {
			return command.get();
		} catch (DataIntegrityViolationException failure) {
			if (!isDuplicateKey(failure)) {
				throw failure;
			}
			// The proxied LedgerService call has already rolled back. This second
			// proxied call opens a fresh transaction to read the committed result.
			return ledger.replay(key, fingerprint);
		}
	}

	private boolean isDuplicateKey(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return UNIQUE_KEY_CONSTRAINT.equals(violation.getConstraintName());
			}
		}
		return false;
	}
}
