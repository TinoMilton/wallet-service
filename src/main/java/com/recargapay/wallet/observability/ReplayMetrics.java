package com.recargapay.wallet.observability;

import com.recargapay.wallet.domain.OperationResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Counts the successful idempotent replays by operation type (techspec section
 * 8): an aspect around the replay read, so LedgerService and
 * IdempotentCommandExecutor stay free of metrics code (techspec section 9.1). A
 * first execution never reaches the replay read, and a replay rejected with
 * IDEMPOTENCY_KEY_REUSED throws before the count, so neither is counted.
 */
// One step above the transaction advice's default order, so the aspect wraps
// the transaction and counts a replay only after its transaction completed
// normally.
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@Aspect
@Component
public class ReplayMetrics {

	private static final String REPLAYS = "wallet_idempotent_replays_total";

	private final MeterRegistry meters;

	public ReplayMetrics(MeterRegistry meters) {
		this.meters = meters;
	}

	@Around("execution(public com.recargapay.wallet.domain.OperationResult com.recargapay.wallet.service.LedgerService.replay(..))")
	public Object replay(ProceedingJoinPoint replay) throws Throwable {
		OperationResult original = (OperationResult) replay.proceed();
		meters.counter(REPLAYS, "type", original.type().name()).increment();
		return original;
	}
}
