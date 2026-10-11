package com.recargapay.wallet.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.math.BigDecimal;

/**
 * The assertions a history test makes about what an observer can see: the
 * balance response at a past instant and the pages of a wallet's ledger
 * (techspec section 9.3, rules 6 to 9).
 */
public final class HistoryAssertions {

	private HistoryAssertions() {
	}

	/** The historical balance response reports the expected balance. */
	public static void assertBalanceAt(Response balanceResponse, String expectedBalance) {
		balanceResponse.then().statusCode(200);
		assertThat(balanceResponse.asString()).contains("\"balance\":" + expectedBalance + ",");
	}

	/** The audit page reports the paging the server applied. */
	public static void assertPageMetadata(Response page, int expectedPage, int expectedSize,
			long expectedTotalEntries) {
		page.then().statusCode(200);
		assertThat(page.jsonPath().getInt("page")).isEqualTo(expectedPage);
		assertThat(page.jsonPath().getInt("size")).isEqualTo(expectedSize);
		assertThat(page.jsonPath().getLong("totalEntries")).isEqualTo(expectedTotalEntries);
	}

	/** How many entries the audit page carries. */
	public static void assertEntryCount(Response page, int expectedCount) {
		assertThat(page.jsonPath().getList("entries")).hasSize(expectedCount);
	}

	/**
	 * The entry at a position of the page, exactly as the ledger recorded it. Each
	 * entry also names the operation that caused it, the audit trail's link. Money
	 * is compared by value (techspec section 9.3, rule 9), because a JSON reader is
	 * free to drop a trailing zero.
	 */
	public static void assertEntry(Response page, int position, String expectedSeq, String expectedType,
			String expectedAmountSigned, String expectedBalanceAfter, String expectedCreatedAt) {
		String entry = "entries[" + position + "].";
		assertThat(page.jsonPath().getUUID(entry + "operationId")).isNotNull();
		assertThat(page.jsonPath().getString(entry + "seq")).isEqualTo(expectedSeq);
		assertThat(page.jsonPath().getString(entry + "type")).isEqualTo(expectedType);
		assertThat(theMoneyAt(page, entry + "amountSigned")).isEqualByComparingTo(expectedAmountSigned);
		assertThat(theMoneyAt(page, entry + "balanceAfter")).isEqualByComparingTo(expectedBalanceAfter);
		assertThat(page.jsonPath().getString(entry + "createdAt")).isEqualTo(expectedCreatedAt);
	}

	/** The money a field of the page holds, as a plain decimal. */
	private static BigDecimal theMoneyAt(Response page, String field) {
		return new BigDecimal(page.jsonPath().getString(field));
	}
}
