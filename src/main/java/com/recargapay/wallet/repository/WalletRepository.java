package com.recargapay.wallet.repository;

import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, WalletId> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Wallet w where w.id = :walletId")
	Optional<Wallet> lockById(@Param("walletId") WalletId walletId);
}
