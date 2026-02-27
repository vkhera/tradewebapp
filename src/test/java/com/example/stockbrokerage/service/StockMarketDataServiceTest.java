package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.MockYahooFinanceClient;
import com.example.stockbrokerage.repository.StockPriceCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link StockMarketDataService} mirrors price data to PostgreSQL
 * via {@link StockPriceCacheRepository#upsertBar} when prices are fetched and cached.
 *
 * <p>The service caches prices to CSV (stock_predictions/{symbol}_prices.csv).
 * We delete those files before each test so the service always does a fresh Yahoo
 * Finance fetch, guaranteeing that saveToCsvCache (and thus upsertBar) is called.
 */
class StockMarketDataServiceTest {

    // Unique symbols not shared with other test classes to avoid cross-test CSV contamination
    private static final String SYM_A = "SVCTEST_A";
    private static final String SYM_B = "SVCTEST_B";
    private static final String SYM_C = "SVCTEST_C";

    private StockMarketDataService service;
    private StockPriceCacheRepository cacheRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Remove any stale CSV cache files so the service always fetches fresh data
        Files.createDirectories(Path.of("stock_predictions"));
        for (String sym : List.of(SYM_A, SYM_B, SYM_C)) {
            Files.deleteIfExists(Path.of("stock_predictions", sym + "_prices.csv"));
        }
        MockYahooFinanceClient mockClient = new MockYahooFinanceClient();
        cacheRepository = mock(StockPriceCacheRepository.class);
        service = new StockMarketDataService(mockClient, cacheRepository);
    }

    @Test
    void getPrices_callsUpsertBarForEachPriceFetched() {
        List<BigDecimal> prices = service.getPrices(SYM_A, 10);
        assertThat(prices).isNotEmpty();

        verify(cacheRepository, atLeast(prices.size()))
            .upsertBar(eq(SYM_A), any(LocalDateTime.class), any(BigDecimal.class), any(LocalDateTime.class));
    }

    @Test
    void getPrices_upsertBarReceivesCorrectSymbol() {
        service.getPrices(SYM_B, 5);

        ArgumentCaptor<String> symbolCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheRepository, atLeastOnce())
            .upsertBar(symbolCaptor.capture(), any(), any(), any());

        assertThat(symbolCaptor.getAllValues()).allMatch(s -> s.equals(SYM_B));
    }

    @Test
    void getPrices_upsertBarReceivesPositivePrices() {
        service.getPrices(SYM_C, 5);

        ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(cacheRepository, atLeastOnce())
            .upsertBar(any(), any(), priceCaptor.capture(), any());

        assertThat(priceCaptor.getAllValues()).allMatch(p -> p.compareTo(BigDecimal.ZERO) > 0);
    }
}
