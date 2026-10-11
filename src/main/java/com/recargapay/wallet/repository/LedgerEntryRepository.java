package com.recargapay.wallet.repository;

import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.WalletId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	@org.springframework.data.jpa.repository.Query("select e from LedgerEntry e where e.wallet.id = :walletId order by e.sequence desc limit 1")
	Optional<LedgerEntry> latestFor(WalletId walletId);
}
