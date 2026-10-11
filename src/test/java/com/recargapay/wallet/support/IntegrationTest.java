package com.recargapay.wallet.support;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for every integration test: the application runs in the same JVM
 * on a random port, against a real PostgreSQL in Testcontainers, and REST
 * Assured talks to it over HTTP (techspec section 9.4). Money-moving rules are
 * never tested against a mocked database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class IntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	@LocalServerPort
	private int serverPort;

	@BeforeEach
	void pointRestAssuredAtTheRunningApplication() {
		RestAssured.baseURI = "http://localhost";
		RestAssured.port = serverPort;
	}
}
