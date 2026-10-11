package com.recargapay.wallet.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.service.RequestFingerprint;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LedgerAssertions {

	private final JdbcTemplate sql;

	public LedgerAssertions(JdbcTemplate sql) {
		this.sql = sql;
	}

	public void assertLedgerEntryCount(WalletId wallet, int expected) {
		assertThat(sql.queryForObject("select count(*) from ledger_entry where wallet_id = ?", Integer.class,
				wallet.value())).isEqualTo(expected);
	}

	public void assertEntry(WalletId wallet, long sequence, String signedAmount, String balanceAfter) {
		List<Entry> recorded = sql.query(
				"select amount_signed, balance_after from ledger_entry where wallet_id = ? and seq = ?",
				(rs, row) -> new Entry(rs.getBigDecimal(1), rs.getBigDecimal(2)), wallet.value(), sequence);
		assertThat(recorded).hasSize(1);
		assertThat(recorded.getFirst().amount()).isEqualByComparingTo(signedAmount);
		assertThat(recorded.getFirst().balance()).isEqualByComparingTo(balanceAfter);
	}

	public void assertOperation(Response response, WalletId wallet, String type, String amount, String balance) {
		response.then().statusCode(201);
		assertThat(response.jsonPath().getString("type")).isEqualTo(type);
		assertThat(response.jsonPath().getUUID("walletId")).isEqualTo(wallet.value());
		assertThat(response.asString()).contains("\"amount\":" + amount, "\"balance\":" + balance);
		UUID id = response.jsonPath().getUUID("operationId");
		assertThat(sql.queryForObject(
				"select count(*) from operation where id = ? and type = ? "
						+ "and length(request_fingerprint) = 64 and idempotency_key is not null",
				Integer.class, id, type)).isEqualTo(1);
		assertThat(sql.queryForObject("select count(*) from ledger_entry where operation_id = ?", Integer.class, id))
				.isEqualTo(1);
		assertThat(sql.queryForObject("select count(*) from outbox_event where aggregate_id = ?", Integer.class, id))
				.isEqualTo(1);
	}

	public void assertAuditInvariant(WalletId wallet) {
		BigDecimal cached = sql.queryForObject("select balance from wallet where id = ?", BigDecimal.class,
				wallet.value());
		BigDecimal sum = sql.queryForObject(
				"select coalesce(sum(amount_signed), 0) from ledger_entry where wallet_id = ?", BigDecimal.class,
				wallet.value());
		BigDecimal last = sql
				.query("select balance_after from ledger_entry where wallet_id = ? order by seq desc limit 1",
						(rs, row) -> rs.getBigDecimal(1), wallet.value())
				.stream().findFirst().orElse(BigDecimal.ZERO);
		assertThat(cached).isEqualByComparingTo(sum).isEqualByComparingTo(last);
	}

	public void assertContiguousSequences(WalletId wallet, int count) {
		List<Long> sequences = sql.query("select seq from ledger_entry where wallet_id = ? order by seq",
				(rs, row) -> rs.getLong(1), wallet.value());
		assertThat(sequences).containsExactlyElementsOf(LongStream.rangeClosed(1, count).boxed().toList());
	}

	public void assertRecordedAt(WalletId wallet, long sequence, String instant) {
		OffsetDateTime recordedAt = sql.queryForObject(
				"select created_at from ledger_entry where wallet_id = ? and seq = ?", OffsetDateTime.class,
				wallet.value(), sequence);
		assertThat(recordedAt.toInstant()).isEqualTo(Instant.parse(instant));
	}

	public void assertRecordedTimeIncreasesWithSequence(WalletId wallet) {
		List<Instant> recordedTimes = sql.query("select created_at from ledger_entry where wallet_id = ? order by seq",
				(rs, row) -> rs.getObject(1, OffsetDateTime.class).toInstant(), wallet.value());
		assertThat(recordedTimes).hasSize(2);
		assertThat(recordedTimes.get(1)).isAfter(recordedTimes.get(0));
	}

	public void assertStoredRequest(Response response, WalletId wallet, String key, String amount) {
		UUID operationId = response.jsonPath().getUUID("operationId");
		List<String> fingerprints = sql.query(
				"select request_fingerprint from operation where id = ? and idempotency_key = ?",
				(rs, row) -> rs.getString(1).trim(), operationId, key);
		assertThat(fingerprints)
				.containsExactly(new RequestFingerprint().forRequest(OperationType.DEPOSIT, wallet, Money.of(amount)));
	}

	private record Entry(BigDecimal amount, BigDecimal balance) {
	}
}
