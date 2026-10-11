package com.recargapay.wallet.outbox;

import com.recargapay.wallet.outbox.events.WalletEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an event's payload as the plain JSON stored in the outbox row, with
 * the same mapper the API uses.
 */
@Component
public class EventPayloads {

	private final JsonMapper json;

	public EventPayloads(JsonMapper json) {
		this.json = json;
	}

	public String asJson(WalletEvent event) {
		return json.writeValueAsString(event);
	}
}
