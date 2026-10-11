package com.recargapay.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

	@Id
	private UUID id;

	@Column(name = "aggregate_type", length = 32, nullable = false)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "event_type", length = 32, nullable = false)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", columnDefinition = "jsonb", nullable = false)
	private String payload;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	protected OutboxEvent() {
	}

	/**
	 * One unpublished event about the money operation that caused it. The id is
	 * meaningless beyond uniqueness, so it is generated here.
	 */
	public static OutboxEvent forOperation(OperationId operationId, String eventType, String payload,
			Instant occurredAt) {
		return new OutboxEvent(UUID.randomUUID(), operationId.value(), eventType, payload, occurredAt);
	}

	private OutboxEvent(UUID id, UUID aggregateId, String eventType, String payload, Instant occurredAt) {
		this.id = Objects.requireNonNull(id, "event id");
		this.aggregateType = "OPERATION";
		this.aggregateId = Objects.requireNonNull(aggregateId, "aggregate id");
		this.eventType = Objects.requireNonNull(eventType, "event type");
		this.payload = Objects.requireNonNull(payload, "payload");
		this.occurredAt = Objects.requireNonNull(occurredAt, "occurred at");
	}

	public UUID id() {
		return id;
	}

	/** The aggregate the event is about; every event is about a money operation. */
	public String aggregateType() {
		return aggregateType;
	}

	/** The operation id the event is about; consumers deduplicate on it. */
	public UUID aggregateId() {
		return aggregateId;
	}

	public String eventType() {
		return eventType;
	}

	/** The event body as plain JSON. */
	public String payload() {
		return payload;
	}

	public Instant occurredAt() {
		return occurredAt;
	}

	/**
	 * The only field that ever changes: the row is written once with the ledger
	 * entries and only later stamped as published. Unlike a ledger entry, an outbox
	 * row is mutable.
	 */
	public void markPublished(Instant publishedAt) {
		this.publishedAt = Objects.requireNonNull(publishedAt, "published at");
	}
}
