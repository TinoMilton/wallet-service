package com.recargapay.wallet.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What a test can observe about the outbox: the events a committed operation
 * left behind. Every assertion is scoped to the test's own operation or wallet,
 * because the suite shares one database and never asserts global counts
 * (techspec section 9.3).
 */
@Component
public class OutboxAssertions {

	private final JdbcTemplate sql;

	public OutboxAssertions(JdbcTemplate sql) {
		this.sql = sql;
	}

	/**
	 * BR-28: a successful operation emits exactly one event about its operation id.
	 */
	public void assertOneEventFor(UUID operationId, String eventType) {
		assertThat(sql.queryForObject(
				"select count(*) from outbox_event where aggregate_id = ? and aggregate_type = 'OPERATION' "
						+ "and event_type = ?",
				Integer.class, operationId, eventType)).isEqualTo(1);
	}

	/**
	 * The payload carries the given field, such as operationId, walletId, amount or
	 * occurredAt.
	 */
	public void assertPayloadCarries(UUID operationId, String field, String expected) {
		assertThat(sql.queryForObject("select payload ->> ? from outbox_event where aggregate_id = ?", String.class,
				field, operationId)).isEqualTo(expected);
	}

	/** An inserted event waits for its publisher, so it starts unpublished. */
	public void assertEventUnpublished(UUID operationId) {
		assertThat(
				sql.queryForObject("select count(*) from outbox_event where aggregate_id = ? and published_at is null",
						Integer.class, operationId))
				.isEqualTo(1);
	}

	/** A publishing run stamps the event with the clock instant it ran at. */
	public void assertPublishedAt(UUID operationId, String isoInstant) {
		assertThat(sql.queryForObject(
				"select count(*) from outbox_event where aggregate_id = ? and published_at = ?::timestamptz",
				Integer.class, operationId, isoInstant)).isEqualTo(1);
	}

	/**
	 * BR-28: a rejected operation of the given type leaves no event for the wallet.
	 */
	public void assertNoEventOfTypeFor(String eventType, WalletId wallet) {
		assertThat(sql.queryForObject("select count(*) from outbox_event where event_type = ? "
				+ "and (payload ->> 'walletId') = cast(? as text)", Integer.class, eventType, wallet.value())).isZero();
	}
}
