package com.recargapay.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "operation")
public class Operation {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", length = 16, nullable = false)
	private OperationType type;

	@Embedded
	private IdempotencyKey idempotencyKey;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "request_fingerprint", length = 64, nullable = false, columnDefinition = "char(64)")
	private String requestFingerprint;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Operation() {
	}

	public Operation(UUID id, OperationType type, IdempotencyKey idempotencyKey, String requestFingerprint,
			Instant createdAt) {
		this.id = Objects.requireNonNull(id, "operation id");
		this.type = Objects.requireNonNull(type, "type");
		this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key");
		this.requestFingerprint = Objects.requireNonNull(requestFingerprint, "request fingerprint");
		this.createdAt = Objects.requireNonNull(createdAt, "created at");
	}

	public UUID id() {
		return id;
	}

	/** The kind of money movement this operation performed. */
	public OperationType type() {
		return type;
	}
}
