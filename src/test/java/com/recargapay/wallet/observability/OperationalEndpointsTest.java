package com.recargapay.wallet.observability;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

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
		assertThat(readiness.jsonPath().getString("components.db.status")).isEqualTo("UP");
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
	@DisplayName("9.4: the balance operation documents the at parameter, so the historical balance is served too")
	void balanceOperationDocumentsThePastInstant() {
		Response document = given().when().get("/v3/api-docs");

		document.then().statusCode(200);
		List<String> parameters = document.jsonPath()
				.getList("paths['/api/v1/wallets/{walletId}/balance'].get.parameters.name");
		assertThat(parameters).contains("walletId", "at");
	}

	@Test
	@DisplayName("9.4: Swagger UI is served")
	void swaggerUiIsServed() {
		Response ui = given().when().get("/swagger-ui/index.html");

		ui.then().statusCode(200);
	}
}
