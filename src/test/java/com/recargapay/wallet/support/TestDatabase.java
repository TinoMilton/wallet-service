package com.recargapay.wallet.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The tests' window on the real database: the few facts no endpoint can observe
 * yet. The shared test infrastructure wires it with the application's
 * {@link JdbcTemplate} before every test.
 */
public final class TestDatabase {

	private static JdbcTemplate sql;

	private TestDatabase() {
	}

	static void wiredWith(JdbcTemplate jdbcTemplate) {
		TestDatabase.sql = jdbcTemplate;
	}

	/** How many wallets exist for a user. */
	public static int walletsFor(String userId) {
		return sql.queryForObject("select count(*) from wallet where user_id = ?", Integer.class, userId);
	}

	/** How many wallets were created exactly at an instant. */
	public static int walletsCreatedAt(String isoInstant) {
		return sql.queryForObject("select count(*) from wallet where created_at = ?::timestamptz", Integer.class,
				isoInstant);
	}

	/**
	 * Makes wallet inserts fail on a constraint that is not the one-wallet-per-user
	 * rule.
	 */
	public static void rejectWalletInsertsOnAnotherConstraint(String userId) {
		sql.execute("ALTER TABLE wallet ADD CONSTRAINT not_the_one_wallet_per_user_rule " + "CHECK (user_id <> '"
				+ userId + "')");
	}

	/** Undoes {@link #rejectWalletInsertsOnAnotherConstraint(String)}. */
	public static void acceptWalletInsertsAgain() {
		sql.execute("ALTER TABLE wallet DROP CONSTRAINT not_the_one_wallet_per_user_rule");
	}
}
