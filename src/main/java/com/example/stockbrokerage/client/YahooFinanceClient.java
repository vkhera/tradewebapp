package com.example.stockbrokerage.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Abstraction over the Yahoo Finance API.
 * The production implementation ({@link RealYahooFinanceClient}) makes actual HTTP calls.
 * The test implementation ({@link MockYahooFinanceClient}) returns deterministic stub data
 * so the test suite has no external network dependency.
 */
public interface YahooFinanceClient {

    /**
     * Fetch the current market price for the given ticker symbol.
     * Returns {@link BigDecimal#ZERO} when the price cannot be determined.
     */
    BigDecimal getCurrentPrice(String symbol);

    /**
     * Fetch a full quote response for the given ticker symbol.
     * Returns a map with an "error" key when the call fails.
     */
    Map<String, Object> getQuote(String symbol);

    /**
     * Fetch historical 5-minute closing prices for the given ticker symbol.
     * Returns an empty list when data is unavailable.
     * Prices are ordered oldest → newest.
     */
    List<BigDecimal> getHistoricalPrices(String symbol);
}
