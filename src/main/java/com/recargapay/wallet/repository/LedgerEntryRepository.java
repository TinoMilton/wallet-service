package com.recargapay.wallet.repository;

import com.recargapay.wallet.domain.LedgerEntry;
import com.recargapay.wallet.domain.WalletId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	@Query("select e from LedgerEntry e where e.operation.id = :operationId")
	List<LedgerEntry> forOperation(@Param("operationId") UUID operationId);

	@Query("select e from LedgerEntry e where e.wallet.id = :walletId order by e.sequence desc limit 1")
	Optional<LedgerEntry> latestFor(WalletId walletId);

	/**
	 * The last entry of a wallet at or before an instant (BR-13). The
	 * {@code (wallet_id, created_at)} index serves the wallet filter and the
	 * timestamp range, and a plain read takes no lock.
	 */
	@Query("select e from LedgerEntry e where e.wallet.id = :walletId and e.createdAt <= :at "
			+ "order by e.sequence desc limit 1")
	Optional<LedgerEntry> lastEntryAtOrBefore(@Param("walletId") WalletId walletId, @Param("at") Instant at);

	/**
	 * A wallet's ledger, newest first, one page at a time. The operation is fetched
	 * with the entry so the audit page can name it: the lazy association could not
	 * be read once the page query's transaction has ended.
	 */
	@Query(value = "select e from LedgerEntry e join fetch e.operation where e.wallet.id = :walletId "
			+ "order by e.sequence desc", countQuery = "select count(e) from LedgerEntry e where e.wallet.id = :walletId")
	Page<LedgerEntry> pageFor(@Param("walletId") WalletId walletId, Pageable pageable);
}
