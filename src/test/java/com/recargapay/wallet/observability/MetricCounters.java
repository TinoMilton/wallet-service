package com.recargapay.wallet.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * The monitoring tests' window on the problem and replay counters. All tests
 * share one application and one registry, so a test never asserts an absolute
 * counter value: it reads the counter before the action, acts, and asserts the
 * difference (techspec section 9.3, rule 8). Reading and comparing are hidden
 * here, so the test body stays on the behaviour it verifies.
 */
@Component
public class MetricCounters {

	private final MeterRegistry meters;

	public MetricCounters(MeterRegistry meters) {
		this.meters = meters;
	}

	/**
	 * Reads the problem counter of a code now; the test asserts the difference
	 * after acting.
	 */
	public Count problemsAnsweredWith(String code) {
		return new Count("wallet_api_problems_total", "code", code);
	}

	/**
	 * Reads the idempotent replay counter of an operation type now; the test
	 * asserts the difference after acting.
	 */
	public Count replaysOf(String type) {
		return new Count("wallet_idempotent_replays_total", "type", type);
	}

	private double theCount(String name, String tagKey, String tagValue) {
		Counter counter = meters.find(name).tag(tagKey, tagValue).counter();
		return (counter == null) ? 0.0 : counter.count();
	}

	/**
	 * One counter's value at the moment this was read; the test asserts the
	 * difference after acting.
	 */
	public final class Count {

		private final String name;
		private final String tagKey;
		private final String tagValue;
		private final double before;

		private Count(String name, String tagKey, String tagValue) {
			this.name = name;
			this.tagKey = tagKey;
			this.tagValue = tagValue;
			this.before = theCount(name, tagKey, tagValue);
		}

		/**
		 * Asserts the counter rose by exactly the expected number of events since it
		 * was read.
		 */
		public void increasedBy(long expectedIncrease) {
			assertThat(theCount(name, tagKey, tagValue)).isEqualTo(before + expectedIncrease);
		}
	}
}
