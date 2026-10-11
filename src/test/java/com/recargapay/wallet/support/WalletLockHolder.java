package com.recargapay.wallet.support;

import com.recargapay.wallet.domain.WalletId;
import io.restassured.response.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class WalletLockHolder {

	private final DataSource dataSource;

	public WalletLockHolder(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public Response whileWalletLocked(WalletId wallet, Supplier<Response> request) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (var lock = connection.prepareStatement("select id from wallet where id = ? for update")) {
				lock.setObject(1, wallet.value());
				lock.executeQuery().close();
			}
			try {
				return request.get();
			} finally {
				connection.rollback();
			}
		}
	}
}
