package com.recargapay.wallet.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.Balance;
import com.recargapay.wallet.domain.WalletId;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransferAssertions {

	private final JdbcTemplate sql;

	public TransferAssertions(JdbcTemplate sql) {
		this.sql = sql;
	}

	public void assertOneTransferOperationWithTwoEntries(Response transferred) {
		UUID operationId = transferred.jsonPath().getUUID("operationId");
		assertThat(sql.queryForObject("select count(*) from operation where id = ? and type = 'TRANSFER'",
				Integer.class, operationId)).isEqualTo(1);
		assertThat(sql.queryForObject("select count(*) from ledger_entry where operation_id = ?", Integer.class,
				operationId)).isEqualTo(2);
		assertThat(sql.queryForObject("select count(*) from outbox_event where aggregate_id = ? and event_type = ?",
				Integer.class, operationId, "FundsTransferred")).isEqualTo(1);
	}

	public void assertTotalBalance(WalletId[] wallets, Balance expectedTotal) {
		BigDecimal total = BigDecimal.ZERO;
		for (WalletId wallet : wallets) {
			total = total.add(
					sql.queryForObject("select balance from wallet where id = ?", BigDecimal.class, wallet.value()));
		}
		assertThat(total).isEqualByComparingTo(expectedTotal.toString());
	}
}
