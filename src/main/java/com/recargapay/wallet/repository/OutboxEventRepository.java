package com.recargapay.wallet.repository;

import com.recargapay.wallet.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

	/**
	 * The next batch to publish, in occurred_at order. The row lock lets competing
	 * instances skip each other's events instead of waiting for them.
	 */
	@Query(value = "select * from outbox_event where published_at is null "
			+ "order by occurred_at asc limit :batchSize for update skip locked", nativeQuery = true)
	List<OutboxEvent> lockNextUnpublishedBatch(int batchSize);

	/**
	 * How many events still wait for publication; backs the
	 * wallet_outbox_unpublished gauge.
	 */
	long countByPublishedAtIsNull();

	/**
	 * The event that has waited the longest; backs the wallet_outbox_lag_seconds
	 * gauge.
	 */
	Optional<OutboxEvent> findFirstByPublishedAtIsNullOrderByOccurredAtAsc();
}
