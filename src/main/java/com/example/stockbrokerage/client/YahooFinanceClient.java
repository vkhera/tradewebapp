package com.example.stockbrokerage.client;

import com.example.stockbrokerage.dto.DailyBar;

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
     * Fetch the post-market (after-hours) price for the given ticker symbol.
     * Returns {@code null} when post-market data is unavailable (i.e. during regular
     * market hours or when the exchange does not report after-hours data).
     */
    BigDecimal getPostMarketPrice(String symbol);

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

    /**
     * Fetch daily OHLCV bars for the given ticker symbol covering approximately
     * the past {@code days} calendar days (up to 6 months).
     * Returns an empty list when data is unavailable.
     * Bars are ordered oldest → newest.
     */
    List<DailyBar> getDailyBars(String symbol, int days);

    /**
     * Fetch the raw {@code meta} map from the Yahoo Finance v8/chart endpoint for the
     * given symbol.  Useful for retrieving fields like {@code regularMarketPrice},
     * {@code chartPreviousClose}, {@code postMarketPrice}, and {@code shortName} in a
     * single call.
     * <p>
     * The symbol is URL-encoded internally so index tickers like {@code ^GSPC} and
     * {@code GC=F} are handled correctly.
     *
     * @param symbol Yahoo Finance ticker (e.g. "^GSPC", "GC=F", "AAPL")
     * @return meta map, or an empty map when data is unavailable
     */
    Map<String, Object> getChartMeta(String symbol);
}
