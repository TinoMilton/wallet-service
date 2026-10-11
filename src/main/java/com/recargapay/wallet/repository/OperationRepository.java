package com.recargapay.wallet.repository;

import com.recargapay.wallet.domain.Operation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationRepository extends JpaRepository<Operation, UUID> {
}
