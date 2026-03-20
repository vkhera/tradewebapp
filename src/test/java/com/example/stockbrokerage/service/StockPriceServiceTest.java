package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.MockYahooFinanceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link StockPriceService}.
 * <p>
 * Uses {@link MockYahooFinanceClient} directly — no Spring context, no network calls,
 * sub-millisecond execution.
 */
class StockPriceServiceTest {

    private StockPriceService service;

    @BeforeEach
    void setUp() {
        service = new StockPriceService(new MockYahooFinanceClient());
    }

    @Test
    void getCurrentPrice_returnsPositiveNonZeroPrice() {
        BigDecimal price = service.getCurrentPrice("AAPL");
        assertThat(price).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void getCurrentPrice_isConsistentForSameSymbol() {
        BigDecimal first  = service.getCurrentPrice("NVDA");
        BigDecimal second = service.getCurrentPrice("NVDA");
        assertThat(first).isEqualByComparingTo(second);
    }

    @Test
    void getCurrentPrice_differsBySymbol() {
        BigDecimal aapl = service.getCurrentPrice("AAPL");
        BigDecimal goog = service.getCurrentPrice("GOOG");
        assertThat(aapl).isNotEqualByComparingTo(goog);
    }

    @Test
    void getQuote_containsChartData() {
        Map<String, Object> quote = service.getQuote("TQQQ");
        assertThat(quote).containsKey("chart");
    }

    @Test
    void getQuote_priceMatchesGetCurrentPrice() {
        String symbol = "JPM";
        BigDecimal expectedPrice = service.getCurrentPrice(symbol);

        @SuppressWarnings("unchecked")
        var chart   = (Map<String, Object>) service.getQuote(symbol).get("chart");
        @SuppressWarnings("unchecked")
        var results = (java.util.List<Map<String, Object>>) chart.get("result");
        @SuppressWarnings("unchecked")
        var meta    = (Map<String, Object>) results.getFirst().get("meta");

        double quotePrice = (double) meta.get("regularMarketPrice");
        assertThat(BigDecimal.valueOf(quotePrice)).isEqualByComparingTo(expectedPrice);
    }

    // ── getPostMarketPrice ────────────────────────────────────────────────────

    @Test
    void getPostMarketPrice_returnsNonNullFromMock() {
        // MockYahooFinanceClient returns seedPrice + $2.00 to simulate after-hours data.
        BigDecimal postPrice = service.getPostMarketPrice("AAPL");
        assertThat(postPrice).isNotNull();
    }

    @Test
    void getPostMarketPrice_isPositive() {
        BigDecimal postPrice = service.getPostMarketPrice("TQQQ");
        assertThat(postPrice).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void getPostMarketPrice_isGreaterThanRegularPrice() {
        // Mock contract: postMarketPrice = seedPrice + 2.0, so it is always above close.
        BigDecimal regular  = service.getCurrentPrice("NVDA");
        BigDecimal postMarket = service.getPostMarketPrice("NVDA");
        assertThat(postMarket).isGreaterThan(regular);
    }

    @Test
    void getPostMarketPrice_differsBySymbol() {
        BigDecimal aapl = service.getPostMarketPrice("AAPL");
        BigDecimal nvda = service.getPostMarketPrice("NVDA");
        assertThat(aapl).isNotEqualByComparingTo(nvda);
    }

    @Test
    void getPostMarketPrice_isConsistentAcrossCallsForSameSymbol() {
        BigDecimal first  = service.getPostMarketPrice("IWM");
        BigDecimal second = service.getPostMarketPrice("IWM");
        assertThat(first).isEqualByComparingTo(second);
    }
}
