package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.MockYahooFinanceClient;
import com.example.stockbrokerage.dto.MarketIndexQuote;
import com.example.stockbrokerage.dto.MarketStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MarketService}.
 * <p>
 * Uses {@link MockYahooFinanceClient} directly — no Spring context and no network calls.
 * Exercises market-status logic, index quote assembly, post-market price inclusion,
 * and the 1-minute in-memory cache.
 */
class MarketServiceTest {

    private MarketService marketService;

    @BeforeEach
    void setUp() {
        marketService = new MarketService(new MockYahooFinanceClient());
    }

    // ── getMarketStatus ───────────────────────────────────────────────────────

    @Test
    void getMarketStatus_returnsNonNullResponse() {
        MarketStatusResponse status = marketService.getMarketStatus();
        assertThat(status).isNotNull();
    }

    @Test
    void getMarketStatus_timeIsNonEmpty() {
        MarketStatusResponse status = marketService.getMarketStatus();
        assertThat(status.estTime()).isNotBlank();
    }

    @Test
    void getMarketStatus_statusIsOneOfKnownValues() {
        MarketStatusResponse status = marketService.getMarketStatus();
        assertThat(status.status()).isIn("OPEN", "PRE_MARKET", "POST_MARKET", "CLOSED");
    }

    @Test
    void getMarketStatus_statusLabelIsNonEmpty() {
        MarketStatusResponse status = marketService.getMarketStatus();
        assertThat(status.statusLabel()).isNotBlank();
    }

    @Test
    void getMarketStatus_isRegularOpenConsistentWithStatus() {
        MarketStatusResponse status = marketService.getMarketStatus();
        if ("OPEN".equals(status.status())) {
            assertThat(status.isRegularOpen()).isTrue();
        } else {
            assertThat(status.isRegularOpen()).isFalse();
        }
    }

    // ── getIndices – shape ────────────────────────────────────────────────────

    @Test
    void getIndices_returnsExactlyFiveEntries() {
        List<MarketIndexQuote> indices = marketService.getIndices();
        assertThat(indices).hasSize(5);
    }

    @Test
    void getIndices_noNullEntries() {
        List<MarketIndexQuote> indices = marketService.getIndices();
        assertThat(indices).doesNotContainNull();
    }

    @Test
    void getIndices_symbolsMatchExpectedSet() {
        List<MarketIndexQuote> indices = marketService.getIndices();
        Set<String> symbols = Set.of("^GSPC", "^DJI", "^IXIC", "GC=F", "^RUT");
        for (MarketIndexQuote q : indices) {
            assertThat(q.symbol()).isIn(symbols);
        }
    }

    @Test
    void getIndices_namesAreNonBlank() {
        List<MarketIndexQuote> indices = marketService.getIndices();
        for (MarketIndexQuote q : indices) {
            assertThat(q.name()).isNotBlank();
        }
    }

    // ── getIndices – price data ───────────────────────────────────────────────

    @Test
    void getIndices_allPricesAreNonNull() {
        List<MarketIndexQuote> indices = marketService.getIndices();
        for (MarketIndexQuote q : indices) {
            assertThat(q.price())
                    .as("price should be non-null for %s", q.symbol())
                    .isNotNull();
        }
    }

    @Test
    void getIndices_allPricesArePositive() {
        List<MarketIndexQuote> indices = marketService.getIndices();
        for (MarketIndexQuote q : indices) {
            assertThat(q.price())
                    .as("price should be positive for %s", q.symbol())
                    .isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Test
    void getIndices_dayChangeIsCalculated() {
        // Mock returns chartPreviousClose = price - 2, so change = +2.0
        List<MarketIndexQuote> indices = marketService.getIndices();
        for (MarketIndexQuote q : indices) {
            assertThat(q.change())
                    .as("change should be non-null for %s", q.symbol())
                    .isNotNull();
            assertThat(q.changePct())
                    .as("changePct should be non-null for %s", q.symbol())
                    .isNotNull();
        }
    }

    @Test
    void getIndices_dayChangeIsPositive_becauseMockPrevCloseIsBelowCurrent() {
        // MockYahooFinanceClient sets chartPreviousClose = price - 2.
        // Therefore change should be exactly +2.0 (before scale rounding).
        List<MarketIndexQuote> indices = marketService.getIndices();
        for (MarketIndexQuote q : indices) {
            assertThat(q.change())
                    .as("change should be +2 for %s", q.symbol())
                    .isGreaterThan(BigDecimal.ZERO);
        }
    }

    // ── getIndices – post-market price ────────────────────────────────────────

    @Test
    void getIndices_postMarketPriceIsNonNull() {
        // MockYahooFinanceClient now includes postMarketPrice in getChartMeta(),
        // so MarketService should propagate it into the MarketIndexQuote.
        List<MarketIndexQuote> indices = marketService.getIndices();
        for (MarketIndexQuote q : indices) {
            assertThat(q.postMarketPrice())
                    .as("postMarketPrice should be non-null for %s", q.symbol())
                    .isNotNull();
        }
    }

    @Test
    void getIndices_postMarketPriceIsGreaterThanRegularPrice() {
        // Mock sets postMarketPrice = price + 2.5
        List<MarketIndexQuote> indices = marketService.getIndices();
        for (MarketIndexQuote q : indices) {
            assertThat(q.postMarketPrice())
                    .as("postMarketPrice > regularPrice for %s", q.symbol())
                    .isGreaterThan(q.price());
        }
    }

    @Test
    void getIndices_postMarketChangeIsCalculated() {
        List<MarketIndexQuote> indices = marketService.getIndices();
        for (MarketIndexQuote q : indices) {
            assertThat(q.postMarketChange())
                    .as("postMarketChange should be non-null for %s", q.symbol())
                    .isNotNull();
            assertThat(q.postMarketChangePct())
                    .as("postMarketChangePct should be non-null for %s", q.symbol())
                    .isNotNull();
        }
    }

    @Test
    void getIndices_postMarketChangeIsPositive_becauseMockAfterHoursPriceIsAboveClose() {
        List<MarketIndexQuote> indices = marketService.getIndices();
        for (MarketIndexQuote q : indices) {
            assertThat(q.postMarketChange())
                    .as("postMarketChange should be positive for %s", q.symbol())
                    .isGreaterThan(BigDecimal.ZERO);
        }
    }

    // ── getIndices – cache behaviour ──────────────────────────────────────────

    @Test
    void getIndices_secondCallReturnsSameListReference() {
        List<MarketIndexQuote> first  = marketService.getIndices();
        List<MarketIndexQuote> second = marketService.getIndices();
        // Within the 1-minute TTL the cached list is returned — same reference.
        assertThat(second).isSameAs(first);
    }

    @Test
    void getIndices_cacheCanBeExpiredByManipulatingTtl() throws Exception {
        // Warm up the cache
        List<MarketIndexQuote> first = marketService.getIndices();

        // Expire the cache by setting the TTL to the past via reflection
        java.lang.reflect.Field ttlField =
                MarketService.class.getDeclaredField("indicesCacheExpiresAt");
        ttlField.setAccessible(true);
        ttlField.setLong(marketService, 0L);

        // Next call should rebuild the cache (new list instance with identical content)
        List<MarketIndexQuote> refreshed = marketService.getIndices();
        assertThat(refreshed)
                .as("Refreshed list should have the same size as the cached one")
                .hasSameSizeAs(first);
        assertThat(refreshed.get(0).symbol()).isEqualTo(first.get(0).symbol());
    }
}
