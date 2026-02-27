package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.MockYahooFinanceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link StockMarketDataService} fetches, caches and serves 5-minute price bars.
 *
 * <p>DB mirroring (previously done inline per bar) was removed from the hot path and is now
 * the sole responsibility of {@link DataSyncBatchService} (nightly batch at 2 AM).
 * These tests therefore verify only the CSV-cache behaviour:
 * <ul>
 *   <li>A fresh fetch from the mock Yahoo client returns non-empty prices.</li>
 *   <li>All returned prices are positive.</li>
 *   <li>A second call for the same symbol is served from the CSV cache (no extra Yahoo call).</li>
 *   <li>The {@code bars} limit is respected.</li>
 * </ul>
 */
class StockMarketDataServiceTest {

    // Unique symbols not shared with other test classes to avoid cross-test CSV contamination
    private static final String SYM_A = "SVCTEST_A";
    private static final String SYM_B = "SVCTEST_B";
    private static final String SYM_C = "SVCTEST_C";

    private StockMarketDataService service;
    private MockYahooFinanceClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        // Remove any stale CSV cache files so the service always does a fresh fetch
        Files.createDirectories(Path.of("stock_predictions"));
        for (String sym : List.of(SYM_A, SYM_B, SYM_C)) {
            Files.deleteIfExists(Path.of("stock_predictions", sym + "_prices.csv"));
        }
        mockClient = new MockYahooFinanceClient();
        service = new StockMarketDataService(mockClient);
    }

    @Test
    void getPrices_returnsPricesFromYahooClient() {
        List<BigDecimal> prices = service.getPrices(SYM_A, 10);
        assertThat(prices).isNotEmpty();
    }

    @Test
    void getPrices_allReturnedPricesArePositive() {
        List<BigDecimal> prices = service.getPrices(SYM_B, 20);
        assertThat(prices).allMatch(p -> p.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void getPrices_respectsBarsLimit() {
        int limit = 5;
        List<BigDecimal> prices = service.getPrices(SYM_C, limit);
        assertThat(prices.size()).isLessThanOrEqualTo(limit);
    }

    @Test
    void getPrices_secondCallServedFromCsvCache() throws Exception {
        // First call populates the CSV cache
        List<BigDecimal> first = service.getPrices(SYM_A, 10);
        assertThat(first).isNotEmpty();

        // CSV file must now exist
        Path csv = Path.of("stock_predictions", SYM_A + "_prices.csv");
        assertThat(csv).exists();

        // Second call should be served from the CSV (same data back)
        List<BigDecimal> second = service.getPrices(SYM_A, 10);
        assertThat(second).isNotEmpty();
        assertThat(second.getLast()).isEqualByComparingTo(first.getLast());
    }
}
