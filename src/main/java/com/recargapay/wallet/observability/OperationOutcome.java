package com.recargapay.wallet.observability;

import com.recargapay.wallet.domain.exception.WalletBusyException;
import org.slf4j.event.Level;

/**
 * How a money command ended: the small fixed set of outcomes the operation
 * counter reports (techspec section 8, "Monitoring and Observability"), and the
 * one place that decides at which level the outcome is logged: business
 * rejections at INFO, lock timeouts at WARN, unexpected failures at ERROR.
 */
enum OperationOutcome {

	/**
	 * The command moved money, or an idempotent replay returned the original
	 * result.
	 */
	SUCCESS(Level.INFO),

	/**
	 * A business rejection the domain named, such as insufficient funds or an
	 * invalid request.
	 */
	REJECTED(Level.INFO),

	/**
	 * The wallet lock could not be obtained in time; the same request can be
	 * retried.
	 */
	BUSY(Level.WARN),

	/**
	 * A failure the service does not know how to name; reported, never explained.
	 */
	ERROR(Level.ERROR);

	/**
	 * The package of the failures the domain has named; each of them is a business
	 * rejection.
	 */
	private static final String DOMAIN_FAILURES = "com.recargapay.wallet.domain.exception";

	private final Level level;

	OperationOutcome(Level level) {
		this.level = level;
	}

	Level level() {
		return this.level;
	}

	/** How a command that ended with this failure is counted. */
	static OperationOutcome of(Throwable failure) {
		if (failure instanceof WalletBusyException) {
			return BUSY;
		}
		return failure.getClass().getPackageName().equals(DOMAIN_FAILURES) ? REJECTED : ERROR;
	}
}
