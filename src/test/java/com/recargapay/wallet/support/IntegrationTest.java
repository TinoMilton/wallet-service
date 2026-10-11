package com.recargapay.wallet.support;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for every integration test: one PostgreSQL container and one
 * cached application context for the whole run (Testcontainers' class lifecycle
 * is not used; the container is started by hand and Ryuk reclaims it when the
 * JVM exits). Money-moving rules are never tested against a mocked database.
 * Tests stay independent: each creates its own wallets with fresh ids, and the
 * shared clock is reset to its baseline before every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClock.ClockConfiguration.class)
public abstract class IntegrationTest {

	/**
	 * One container for the whole test run, shared by every application context.
	 */
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	static {
		postgres.start();
	}

	@LocalServerPort
	private int serverPort;

	@Autowired
	private JdbcTemplate sql;

	@BeforeEach
	void pointRestAssuredAtTheRunningApplication() {
		RestAssured.baseURI = "http://localhost";
		RestAssured.port = serverPort;
	}

	@BeforeEach
	void resetTheSharedTestState() {
		TestDatabase.wiredWith(sql);
		TestClock.resetToBaseline();
	}
}
