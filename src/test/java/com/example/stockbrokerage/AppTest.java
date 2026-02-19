package com.example.stockbrokerage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test that verifies the Spring application context loads successfully
 * without any real external dependencies:
 * <ul>
 *   <li>PostgreSQL → replaced by H2 in-memory database (see test application.yml)</li>
 *   <li>Redis → disabled; simple in-process cache used instead</li>
 *   <li>Yahoo Finance API → replaced by {@link com.example.stockbrokerage.client.MockYahooFinanceClient}</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
public class AppTest {

    @Test
    public void contextLoads() {
        // Verifies the full Spring context assembles without errors or network calls.
    }
}
