package com.recargapay.wallet;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.recargapay.wallet.support.IntegrationTest;
import io.restassured.response.Response;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class ApplicationSmokeTest extends IntegrationTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	@DisplayName("the application context starts and wires its beans")
	void applicationContextStarts() {

		// then the context is live and the application's only source of time is in
		// place
		assertThat(applicationContext.getBean(Clock.class)).isNotNull();
	}

	@Test
	@DisplayName("the health endpoint answers UP")
	void healthEndpointAnswersUp() {

		// when the health endpoint is called
		Response response = given().when().get("/actuator/health");

		// then it answers 200 with status UP
		response.then().statusCode(200).body("status", equalTo("UP"));
	}
}
