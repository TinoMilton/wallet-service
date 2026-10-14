package com.recargapay.wallet.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Counts by stable code the problem details the API answers with (techspec
 * section 8). GlobalExceptionHandler calls this from the one place every
 * problem is built, so a new error code is counted without being listed a
 * second time anywhere.
 */
@Component
public class ProblemMetrics {

	private static final String PROBLEMS = "wallet_api_problems_total";

	private final MeterRegistry meters;

	public ProblemMetrics(MeterRegistry meters) {
		this.meters = meters;
	}

	/** Counts one answered problem, tagged with its RFC 7807 code. */
	public void counted(String code) {
		meters.counter(PROBLEMS, "code", code).increment();
	}
}
