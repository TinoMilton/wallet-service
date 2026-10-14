package com.recargapay.wallet.observability;

import static com.recargapay.wallet.support.LedgerActions.deposit;
import static com.recargapay.wallet.support.WalletActions.aWallet;
import static com.recargapay.wallet.support.WalletActions.readBalance;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.recargapay.wallet.domain.WalletId;
import com.recargapay.wallet.support.IntegrationTest;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationalEndpointsTest extends IntegrationTest {

	@Test
	@DisplayName("9.3: the liveness probe reports UP")
	void livenessProbeIsUp() {
		Response liveness = given().when().get("/actuator/health/liveness");

		liveness.then().statusCode(200);
		assertThat(liveness.jsonPath().getString("status")).isEqualTo("UP");
	}

	@Test
	@DisplayName("9.3: the readiness probe reports UP with the database available")
	void readinessProbeIsUpWithTheDatabase() {
		Response readiness = given().when().get("/actuator/health/readiness");

		readiness.then().statusCode(200);
		assertThat(readiness.jsonPath().getString("status")).isEqualTo("UP");
	}

	@Test
	@DisplayName("9.4: the OpenAPI document is served and lists every endpoint")
	void openApiDocumentListsEveryEndpoint() {
		Response document = given().when().get("/v3/api-docs");

		document.then().statusCode(200);
		assertThat(document.jsonPath().getMap("paths").keySet()).contains("/api/v1/wallets",
				"/api/v1/wallets/{walletId}/balance", "/api/v1/wallets/{walletId}/entries",
				"/api/v1/wallets/{walletId}/deposits", "/api/v1/wallets/{walletId}/withdrawals", "/api/v1/transfers");
	}

	@Test
	@DisplayName("9.4: the balance operation documents the at parameter as optional, so the historical balance is served too")
	void balanceOperationDocumentsThePastInstant() {
		Response document = given().when().get("/v3/api-docs");

		document.then().statusCode(200);
		List<String> parameters = document.jsonPath()
				.getList("paths['/api/v1/wallets/{walletId}/balance'].get.parameters.name");
		assertThat(parameters).contains("walletId", "at");
		Boolean atIsRequired = document.jsonPath().getBoolean(
				"paths['/api/v1/wallets/{walletId}/balance'].get.parameters.find { it.name == 'at' }.required");
		assertThat(atIsRequired).isFalse();
	}

	@Test
	@DisplayName("9.1: the Prometheus endpoint serves the operation counter of a completed deposit")
	void prometheusEndpointServesTheOperationCounter() {
		WalletId wallet = aWallet();

		deposit(wallet, "50.00");
		Response metrics = given().when().get("/actuator/prometheus");

		metrics.then().statusCode(200);
		assertThat(metrics.getContentType()).contains("text/plain");
		assertThat(metrics.asString()).contains("# TYPE wallet_operations_total counter",
				"wallet_operations_total{application=\"wallet-service\",outcome=\"SUCCESS\",type=\"DEPOSIT\"}");
	}

	@Test
	@DisplayName("8: the Prometheus text serves histogram buckets for the HTTP request timer")
	void prometheusServesBucketsForTheHttpRequestTimer() {
		WalletId wallet = aWallet();

		readBalance(wallet).then().statusCode(200);
		Response metrics = given().when().get("/actuator/prometheus");

		metrics.then().statusCode(200);
		assertThat(metrics.asString()).contains("http_server_requests_seconds_bucket{");
	}

	@Test
	@DisplayName("8: the Prometheus text serves histogram buckets for the operation timer")
	void prometheusServesBucketsForTheOperationTimer() {
		WalletId wallet = aWallet();

		deposit(wallet, "50.00").then().statusCode(201);
		Response metrics = given().when().get("/actuator/prometheus");

		metrics.then().statusCode(200);
		assertThat(metrics.asString()).contains("wallet_operation_duration_seconds_bucket{");
	}

	@Test
	@DisplayName("8: every served meter carries the common application tag")
	void everyServedMeterCarriesTheApplicationTag() {
		WalletId wallet = aWallet();

		deposit(wallet, "50.00").then().statusCode(201);
		Response metrics = given().when().get("/actuator/prometheus");

		metrics.then().statusCode(200);
		List<String> samples = sampleLinesOf(metrics);
		assertThat(samples).isNotEmpty();
		assertThat(samples).allMatch(line -> line.contains("application=\"wallet-service\""));
	}

	/**
	 * The served text without its comments and blank lines: one line per meter
	 * sample.
	 */
	private List<String> sampleLinesOf(Response metrics) {
		return metrics.asString().lines().filter(line -> !line.startsWith("#") && !line.isBlank()).toList();
	}

	@Test
	@DisplayName("9.1: the Prometheus endpoint also serves the outbox gauges")
	void prometheusEndpointServesTheOutboxGauges() {
		Response metrics = given().when().get("/actuator/prometheus");

		metrics.then().statusCode(200);
		assertThat(metrics.asString()).contains("wallet_outbox_unpublished", "wallet_outbox_lag_seconds");
	}

	@Test
	@DisplayName("9.4: Swagger UI is served")
	void swaggerUiIsServed() {
		Response ui = given().when().get("/swagger-ui/index.html");

		ui.then().statusCode(200);
	}

	@Test
	@DisplayName("the manual test console is served at / as HTML")
	void manualTestConsoleIsServed() {
		Response console = given().when().get("/");

		console.then().statusCode(200);
		assertThat(console.getContentType()).contains("text/html");
	}
}
