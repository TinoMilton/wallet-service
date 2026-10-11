package com.recargapay.wallet.repository;

import com.recargapay.wallet.domain.Wallet;
import com.recargapay.wallet.domain.WalletId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, WalletId> {
}
