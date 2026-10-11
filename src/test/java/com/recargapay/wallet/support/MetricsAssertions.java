package com.recargapay.wallet.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * The tests' window on the operation metrics. All tests share one application
 * and one metrics registry, so a test never asserts an absolute counter value:
 * it reads the counter before the action, acts, and asserts the difference.
 * Reading and comparing are hidden here, so the test body stays on the rule
 * (techspec section 9.3, rule 8).
 */
@Component
public class MetricsAssertions {

	private final MeterRegistry meters;

	public MetricsAssertions(MeterRegistry meters) {
		this.meters = meters;
	}

	/**
	 * Reads the operation counter for a type and outcome now; the test asserts the
	 * difference after acting.
	 */
	public OperationCount countOf(String type, String outcome) {
		return new OperationCount(type, outcome, theCount(type, outcome));
	}

	/**
	 * Reads how often the operation timer has timed a type now; the test asserts
	 * the difference after acting.
	 */
	public Timings timingsOf(String type) {
		return new Timings(type, theTimings(type));
	}

	private double theCount(String type, String outcome) {
		Counter counter = meters.find("wallet_operations_total").tag("type", type).tag("outcome", outcome).counter();
		return (counter == null) ? 0.0 : counter.count();
	}

	private long theTimings(String type) {
		Timer timer = meters.find("wallet_operation_duration_seconds").tag("type", type).timer();
		return (timer == null) ? 0L : timer.count();
	}

	/**
	 * The wallet operations of one type and outcome, counted so far at the moment
	 * this was read.
	 */
	public final class OperationCount {

		private final String type;
		private final String outcome;
		private final double before;

		private OperationCount(String type, String outcome, double before) {
			this.type = type;
			this.outcome = outcome;
			this.before = before;
		}

		/**
		 * Asserts the counter rose by exactly the expected number of operations since
		 * it was read.
		 */
		public void increasedBy(long expectedIncrease) {
			assertThat(theCount(type, outcome)).isEqualTo(before + expectedIncrease);
		}
	}

	/**
	 * The wallet operations of one type, timed so far at the moment this was read.
	 */
	public final class Timings {

		private final String type;
		private final long before;

		private Timings(String type, long before) {
			this.type = type;
			this.before = before;
		}

		/**
		 * Asserts the timer counted exactly the expected number of operations since it
		 * was read.
		 */
		public void increasedBy(long expectedIncrease) {
			assertThat(theTimings(type)).isEqualTo(before + expectedIncrease);
		}
	}
}
