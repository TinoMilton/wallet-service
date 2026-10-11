package com.recargapay.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.IdempotencyKey;
import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.Money;
import com.recargapay.wallet.domain.Operation;
import com.recargapay.wallet.domain.OperationType;
import com.recargapay.wallet.domain.OutboxEvent;
import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.repository.LedgerEntryRepository;
import com.recargapay.wallet.repository.OperationRepository;
import com.recargapay.wallet.repository.OutboxEventRepository;
import com.recargapay.wallet.repository.WalletRepository;
import com.recargapay.wallet.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

// Testcontainers stops the shared container after this class; its context must not be reused.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LedgerImmutabilityTest extends IntegrationTest {

	private static final Instant RECORDED_AT = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired
	private WalletRepository wallets;

	@Autowired
	private OperationRepository operations;

	@Autowired
	private LedgerEntryRepository entries;

	@Autowired
	private OutboxEventRepository events;

	@Autowired
	private JdbcTemplate sql;

	@Test
	@DisplayName("BR-18: direct SQL cannot update a ledger entry")
	void updateIsRejected() {
		UUID entryId = recordedDeposit();

		Throwable failure = catchThrowable(
				() -> sql.update("UPDATE ledger_entry SET amount_signed = 20.00 WHERE id = ?", entryId));

		assertThat(failure).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("ledger entries cannot be updated");
		assertThat(amountOf(entryId)).isEqualTo("10.00");
	}

	@Test
	@DisplayName("BR-18: direct SQL cannot delete a ledger entry")
	void deleteIsRejected() {
		UUID entryId = recordedWithdrawal();

		Throwable failure = catchThrowable(() -> sql.update("DELETE FROM ledger_entry WHERE id = ?", entryId));

		assertThat(failure).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("ledger entries cannot be updated");
		assertThat(amountOf(entryId)).isEqualTo("-10.00");
	}

	private UUID recordedDeposit() {
		WalletId walletId = new WalletId(UUID.randomUUID());
		Wallet wallet = wallets.saveAndFlush(new Wallet(walletId, walletId.value().toString(), RECORDED_AT));
		UUID operationId = UUID.randomUUID();
		Operation operation = operations.saveAndFlush(new Operation(operationId, OperationType.DEPOSIT,
				new IdempotencyKey(UUID.randomUUID().toString()), "a".repeat(64), RECORDED_AT));
		UUID entryId = UUID.randomUUID();
		entries.saveAndFlush(new LedgerEntry(entryId, operation, wallet, 1, Money.of("10.00"), false,
				Balance.of(Money.of("10.00")), RECORDED_AT));
		events.saveAndFlush(new OutboxEvent(UUID.randomUUID(), operationId, "FundsDeposited", "{}", RECORDED_AT));
		sql.update("UPDATE wallet SET balance = 10.00 WHERE id = ?", walletId.value());
		return entryId;
	}

	private UUID recordedWithdrawal() {
		UUID depositId = recordedDeposit();
		UUID walletUuid = sql.queryForObject("SELECT wallet_id FROM ledger_entry WHERE id = ?", UUID.class, depositId);
		Wallet wallet = wallets.getReferenceById(new WalletId(walletUuid));
		UUID operationId = UUID.randomUUID();
		Instant recordedAt = RECORDED_AT.plusSeconds(1);
		Operation operation = operations.saveAndFlush(new Operation(operationId, OperationType.WITHDRAWAL,
				new IdempotencyKey(UUID.randomUUID().toString()), "b".repeat(64), recordedAt));
		UUID entryId = UUID.randomUUID();
		entries.saveAndFlush(
				new LedgerEntry(entryId, operation, wallet, 2, Money.of("10.00"), true, Balance.zero(), recordedAt));
		events.saveAndFlush(new OutboxEvent(UUID.randomUUID(), operationId, "FundsWithdrawn", "{}", recordedAt));
		sql.update("UPDATE wallet SET balance = 0.00 WHERE id = ?", walletUuid);
		return entryId;
	}

	private String amountOf(UUID entryId) {
		return sql.queryForObject("SELECT amount_signed FROM ledger_entry WHERE id = ?",
				(result, row) -> result.getBigDecimal(1).toPlainString(), entryId);
	}
}
