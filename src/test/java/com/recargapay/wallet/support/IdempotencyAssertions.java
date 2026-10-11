package com.recargapay.wallet.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyAssertions {

	private final JdbcTemplate sql;

	public IdempotencyAssertions(JdbcTemplate sql) {
		this.sql = sql;
	}

	public void assertOperationCount(String key, int expected) {
		assertThat(sql.queryForObject("select count(*) from operation where idempotency_key = ?", Integer.class, key))
				.isEqualTo(expected);
	}

	public void assertEntryCount(String key, int expected) {
		assertThat(sql.queryForObject("select count(*) from ledger_entry e join operation o on o.id = e.operation_id "
				+ "where o.idempotency_key = ?", Integer.class, key)).isEqualTo(expected);
	}
}
