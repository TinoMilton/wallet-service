package com.recargapay.wallet.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A {@link Clock} whose instant the tests control, so a test never depends on
 * the wall clock (techspec section 9.3, rule 5). Install it as the
 * application's {@code Clock} bean, then move it with {@link #setTo(Instant)}
 * or {@link #advanceBy(Duration)}.
 */
public final class MutableClock extends Clock {

	private Instant now;
	private final ZoneId zone;

	private MutableClock(Instant initialInstant, ZoneId zone) {
		this.now = initialInstant;
		this.zone = zone;
	}

	public static MutableClock at(Instant instant) {
		return new MutableClock(instant, ZoneOffset.UTC);
	}

	public void setTo(Instant instant) {
		this.now = instant;
	}

	public void advanceBy(Duration duration) {
		this.now = now.plus(duration);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(now, zone);
	}

	@Override
	public Instant instant() {
		return now;
	}
}
