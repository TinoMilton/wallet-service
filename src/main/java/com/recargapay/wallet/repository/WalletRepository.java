package com.recargapay.wallet.repository;

import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, WalletId> {

	/**
	 * The current-balance read of techspec section 5: the cached balance column
	 * alone, without loading or locking the wallet row.
	 */
	@Query(value = "select balance from wallet where id = :walletId", nativeQuery = true)
	Optional<BigDecimal> findCurrentBalance(@Param("walletId") UUID walletId);
}
