package com.recargapay.wallet.support;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import com.recargapay.wallet.domain.WalletId;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The intention-revealing actions every wallet test is written with, so a test
 * body contains only what matters to its rule (techspec section 9.3, rule 8).
 * Tests extend this class and get the actions bare: {@code aWallet()},
 * {@code assertBalance(...)}, {@code assertRejected(...)}.
 */
// Testcontainers stops the shared container after each test class; a class's
// context must not be reused.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class WalletTestActions extends IntegrationTest {

	/**
	 * The application's clock, under the tests' control (techspec section 9.3, rule
	 * 5).
	 */
	private static final MutableClock CLOCK = MutableClock.at(Instant.parse("2026-10-11T00:00:00Z"));

	@Autowired
	private JdbcTemplate sql;

	protected static void theTimeIs(String isoInstant) {
		CLOCK.setTo(Instant.parse(isoInstant));
	}

	protected static WalletId aWallet() {
		return aWalletFor("user-" + UUID.randomUUID());
	}

	protected static WalletId aWalletFor(String userId) {
		Response created = createWallet("{\"userId\": \"" + userId + "\"}");
		created.then().statusCode(201);
		return new WalletId(created.jsonPath().getUUID("walletId"));
	}

	protected static Response createWallet(String jsonBody) {
		return given().contentType(ContentType.JSON).body(jsonBody).when().post("/api/v1/wallets");
	}

	protected static void assertWalletCreated(Response created, String expectedUserId, String expectedBalance,
			String expectedCreatedAt) {
		created.then().statusCode(201);
		assertThat(created.jsonPath().getUUID("walletId")).isNotNull();
		assertThat(created.jsonPath().getString("userId")).isEqualTo(expectedUserId);
		assertThat(created.asString()).contains("\"balance\":" + expectedBalance + ",");
		assertThat(created.jsonPath().getString("createdAt")).isEqualTo(expectedCreatedAt);
	}

	protected static Response readBalance(WalletId wallet) {
		return given().when().get("/api/v1/wallets/{walletId}/balance", wallet.value());
	}

	protected static void assertBalance(WalletId wallet, String expectedBalance) {
		Response balance = readBalance(wallet);
		balance.then().statusCode(200);
		assertThat(balance.asString()).contains("\"balance\":" + expectedBalance + ",");
	}

	protected static void assertRejected(Response response, int expectedStatus, String expectedCode) {
		response.then().statusCode(expectedStatus).header("Content-Type", containsString("application/problem+json"));
		assertThat(response.jsonPath().getString("code")).isEqualTo(expectedCode);
	}

	protected void assertWalletCountFor(String userId, int expectedCount) {
		Integer count = sql.queryForObject("select count(*) from wallet where user_id = ?", Integer.class, userId);
		assertThat(count).isEqualTo(expectedCount);
	}

	/**
	 * Replaces the application's clock with the controllable one above. Test
	 * classes import this configuration, and the whole application, server
	 * included, takes its time from the imported clock.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	public static class ControlledClockConfiguration {

		@Bean
		@Primary
		Clock controlledClock() {
			return CLOCK;
		}
	}
}
