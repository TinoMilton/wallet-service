package com.recargapay.wallet.observability;

import com.recargapay.wallet.domain.OperationResult;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.WalletId;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Observes the three money commands from outside: an aspect around the
 * executor's deposit, withdraw and transfer, so the business methods stay free
 * of metrics and logging (techspec section 9.1) and LedgerService still reads
 * as the write flow. Every command is counted by type and outcome, timed by
 * type, and logged as one line that carries the request id, wallet id and
 * operation id through the MDC.
 */
@Aspect
@Component
public class OperationMetrics {

	/**
	 * The log every operation completion is written to, whatever code observed it.
	 */
	private static final Logger operationLog = LoggerFactory.getLogger("wallet.operation");

	private static final String OPERATIONS = "wallet_operations_total";
	private static final String DURATION = "wallet_operation_duration_seconds";
	private static final String WALLET_ID = "walletId";
	private static final String OPERATION_ID = "operationId";

	private final MeterRegistry meters;

	public OperationMetrics(MeterRegistry meters) {
		this.meters = meters;
	}

	@Around("execution(public com.recargapay.wallet.domain.OperationResult com.recargapay.wallet.service.IdempotentCommandExecutor.deposit(..))"
			+ " && args(wallet, ..)")
	public Object deposit(ProceedingJoinPoint command, WalletId wallet) throws Throwable {
		return measure(command, OperationType.DEPOSIT, wallet);
	}

	@Around("execution(public com.recargapay.wallet.domain.OperationResult com.recargapay.wallet.service.IdempotentCommandExecutor.withdraw(..))"
			+ " && args(wallet, ..)")
	public Object withdraw(ProceedingJoinPoint command, WalletId wallet) throws Throwable {
		return measure(command, OperationType.WITHDRAWAL, wallet);
	}

	@Around("execution(public com.recargapay.wallet.domain.OperationResult com.recargapay.wallet.service.IdempotentCommandExecutor.transfer(..))"
			+ " && args(sender, ..)")
	public Object transfer(ProceedingJoinPoint command, WalletId sender) throws Throwable {
		return measure(command, OperationType.TRANSFER, sender);
	}

	/**
	 * Measures one money command: counted whether it succeeds, is rejected or
	 * fails, and timed however it ends. An idempotent replay returns normally and
	 * is counted as a success, because from the caller's point of view it is one.
	 */
	private Object measure(ProceedingJoinPoint command, OperationType type, WalletId wallet) throws Throwable {
		MDC.put(WALLET_ID, wallet.value().toString());
		long startedAt = System.nanoTime();
		try {
			OperationResult result = (OperationResult) command.proceed();
			MDC.put(OPERATION_ID, result.operationId().value().toString());
			recordOutcome(type, OperationOutcome.SUCCESS, null);
			return result;
		} catch (Throwable failure) {
			recordOutcome(type, OperationOutcome.of(failure), failure);
			throw failure;
		} finally {
			meters.timer(DURATION, "type", type.name()).record(Duration.ofNanos(System.nanoTime() - startedAt));
			MDC.remove(OPERATION_ID);
			MDC.remove(WALLET_ID);
		}
	}

	/**
	 * Counts the command by outcome and writes its log line, at the level the
	 * outcome decides.
	 */
	private void recordOutcome(OperationType type, OperationOutcome outcome, Throwable failure) {
		meters.counter(OPERATIONS, "type", type.name(), "outcome", outcome.name()).increment();
		switch (outcome) {
			case SUCCESS -> operationLog.atLevel(outcome.level()).log("wallet operation {} completed", type);
			case REJECTED, BUSY ->
				operationLog.atLevel(outcome.level()).log("wallet operation {}: {}", type, failure.getMessage());
			case ERROR -> operationLog.atLevel(outcome.level()).setCause(failure)
					.log("wallet operation {} failed unexpectedly", type);
		}
	}
}
