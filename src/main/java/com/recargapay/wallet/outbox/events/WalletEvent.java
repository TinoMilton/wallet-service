package com.recargapay.wallet.outbox.events;

import java.time.Instant;

/**
 * The payload of an outbox event: the operation id, the wallet id or ids, the
 * amount and the operation time. Every event carries the operation id so
 * consumers can deduplicate the at-least-once delivery.
 */
public sealed interface WalletEvent permits FundsDeposited, FundsWithdrawn, FundsTransferred {

	/** The event type stored on the outbox row, such as FundsDeposited. */
	String eventType();

	/** The operation time, carried in the payload and stored as occurred_at. */
	Instant occurredAt();
}
