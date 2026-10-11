package com.recargapay.wallet.repository;

import com.recargapay.wallet.domain.Operation;
import com.recargapay.wallet.domain.IdempotencyKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationRepository extends JpaRepository<Operation, UUID> {
	Optional<Operation> findByIdempotencyKey(IdempotencyKey key);
}
