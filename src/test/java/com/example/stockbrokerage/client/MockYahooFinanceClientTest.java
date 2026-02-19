package com.example.stockbrokerage.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates deterministic behaviour of the mock Yahoo Finance client used in tests.
 * These tests have zero network dependencies and run in < 1 second.
 */
class MockYahooFinanceClientTest {

    private final MockYahooFinanceClient client = new MockYahooFinanceClient();

    // -------------------------------------------------------------------------
    // getCurrentPrice
    // -------------------------------------------------------------------------

    @Test
    void getCurrentPrice_returnsPositivePrice() {
        BigDecimal price = client.getCurrentPrice("AAPL");
        assertThat(price).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void getCurrentPrice_isStableAcrossCalls() {
        BigDecimal first  = client.getCurrentPrice("NVDA");
        BigDecimal second = client.getCurrentPrice("NVDA");
        assertThat(first).isEqualByComparingTo(second);
    }

    @Test
    void getCurrentPrice_isDifferentPerSymbol() {
        BigDecimal aapl = client.getCurrentPrice("AAPL");
        BigDecimal nvda = client.getCurrentPrice("NVDA");
        assertThat(aapl).isNotEqualByComparingTo(nvda);
    }

    @Test
    void getCurrentPrice_isInExpectedRange() {
        BigDecimal price = client.getCurrentPrice("TQQQ");
        assertThat(price).isBetween(BigDecimal.valueOf(100), BigDecimal.valueOf(999));
    }

    // -------------------------------------------------------------------------
    // getHistoricalPrices
    // -------------------------------------------------------------------------

    @Test
    void getHistoricalPrices_returnsNonEmptyList() {
        List<BigDecimal> prices = client.getHistoricalPrices("AAPL");
        assertThat(prices).isNotEmpty();
    }

    @Test
    void getHistoricalPrices_allPricesPositive() {
        List<BigDecimal> prices = client.getHistoricalPrices("AAPL");
        assertThat(prices).allMatch(p -> p.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void getHistoricalPrices_isDeterministicAcrossCalls() {
        List<BigDecimal> first  = client.getHistoricalPrices("IWM");
        List<BigDecimal> second = client.getHistoricalPrices("IWM");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void getHistoricalPrices_isDifferentPerSymbol() {
        List<BigDecimal> aapl = client.getHistoricalPrices("AAPL");
        List<BigDecimal> iwm  = client.getHistoricalPrices("IWM");
        // The first element (seed price) will differ between symbols
        assertThat(aapl.getFirst()).isNotEqualByComparingTo(iwm.getFirst());
    }

    // -------------------------------------------------------------------------
    // getQuote
    // -------------------------------------------------------------------------

    @Test
    void getQuote_returnsChartStructure() {
        Map<String, Object> quote = client.getQuote("AAPL");
        assertThat(quote).containsKey("chart");

        @SuppressWarnings("unchecked")
        var chart = (Map<String, Object>) quote.get("chart");
        assertThat(chart).containsKey("result");

        @SuppressWarnings("unchecked")
        var result = (java.util.List<Map<String, Object>>) chart.get("result");
        assertThat(result).isNotEmpty();

        @SuppressWarnings("unchecked")
        var meta = (Map<String, Object>) result.getFirst().get("meta");
        assertThat(meta).containsKey("regularMarketPrice");
    }

    @Test
    void getQuote_regularMarketPriceMatchesGetCurrentPrice() {
        BigDecimal expectedPrice = client.getCurrentPrice("JPM");

        @SuppressWarnings("unchecked")
        var chart = (Map<String, Object>) client.getQuote("JPM").get("chart");
        @SuppressWarnings("unchecked")
        var results = (java.util.List<Map<String, Object>>) chart.get("result");
        @SuppressWarnings("unchecked")
        var meta = (Map<String, Object>) results.getFirst().get("meta");

        double quotePrice = (double) meta.get("regularMarketPrice");
        assertThat(BigDecimal.valueOf(quotePrice)).isEqualByComparingTo(expectedPrice);
    }
}
