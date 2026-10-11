package com.recargapay.wallet.repository;

import com.recargapay.wallet.domain.LedgerEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
}
