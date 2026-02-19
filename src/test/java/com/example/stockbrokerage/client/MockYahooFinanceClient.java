package com.example.stockbrokerage.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only implementation of {@link YahooFinanceClient}.
 * <p>
 * Active when the Spring profile {@code test} is set (e.g. via {@code @ActiveProfiles("test")}
 * or {@code spring.profiles.active=test} in {@code application.yml}).
 * <p>
 * All methods return deterministic, seeded data derived from the ticker symbol so
 * tests can assert specific values without any network access.
 *
 * <h3>Mock price schedule</h3>
 * <ul>
 *   <li>{@link #getCurrentPrice} — a stable value in the range [100, 999] derived from
 *       the symbol's hash code (e.g. AAPL → always the same number).</li>
 *   <li>{@link #getHistoricalPrices} — a list of {@code 4 680} values that form a
 *       deterministic random walk seeded from the symbol hash.</li>
 *   <li>{@link #getQuote} — a minimal Yahoo-Finance-shaped {@code Map} containing
 *       {@code regularMarketPrice} and {@code symbol}.</li>
 * </ul>
 */
@Component
@Profile("test")
@Slf4j
public class MockYahooFinanceClient implements YahooFinanceClient {

    /** Number of 5-min bars returned by {@link #getHistoricalPrices} (matches 60-day range). */
    private static final int DEFAULT_BARS = 4_680;

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        BigDecimal price = seedPrice(symbol);
        log.debug("[MockYahooFinanceClient] getCurrentPrice({}) → {}", symbol, price);
        return price;
    }

    @Override
    public Map<String, Object> getQuote(String symbol) {
        BigDecimal price = seedPrice(symbol);
        Map<String, Object> meta = new HashMap<>();
        meta.put("regularMarketPrice", price.doubleValue());
        meta.put("symbol", symbol);
        meta.put("shortName", symbol + " Inc.");
        meta.put("exchange", "MOCK");

        Map<String, Object> result = new HashMap<>();
        result.put("meta", meta);

        Map<String, Object> chart = new HashMap<>();
        chart.put("result", List.of(result));
        chart.put("error", null);

        Map<String, Object> root = new HashMap<>();
        root.put("chart", chart);

        log.debug("[MockYahooFinanceClient] getQuote({}) → regularMarketPrice={}", symbol, price);
        return root;
    }

    @Override
    public List<BigDecimal> getHistoricalPrices(String symbol) {
        List<BigDecimal> prices = generateDeterministicPrices(symbol, DEFAULT_BARS);
        log.debug("[MockYahooFinanceClient] getHistoricalPrices({}) → {} bars, last={}",
                symbol, prices.size(), prices.getLast());
        return prices;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a stable base price for the given symbol in the range [100, 999].
     * The value is fully determined by the symbol string so every test run gets
     * the same number for the same symbol.
     */
    public static BigDecimal seedPrice(String symbol) {
        int hash = Math.abs(symbol.hashCode());
        double base = 100.0 + (hash % 900);
        return BigDecimal.valueOf(base).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Generates {@code bars} prices using a seeded, deterministic random walk.
     * The seed is derived from the symbol so the same symbol always produces the
     * same series of prices.
     */
    public static List<BigDecimal> generateDeterministicPrices(String symbol, int bars) {
        // Use a simple Linear Congruential Generator seeded by symbol hash
        // so that we have no dependency on java.util.Random's implementation details.
        long seed = symbol.hashCode();
        BigDecimal price = seedPrice(symbol);
        double volatility = 0.001; // 0.1 % per bar — gentle, predictable walk

        List<BigDecimal> prices = new ArrayList<>(bars);
        for (int i = 0; i < bars; i++) {
            seed = lcgNext(seed);
            // Map seed into a small signed change: [-volatility, +volatility]
            double change = ((seed & 0xFFFFL) / 65535.0 - 0.5) * 2 * volatility;
            price = price.multiply(BigDecimal.valueOf(1.0 + change))
                    .setScale(4, RoundingMode.HALF_UP);
            if (price.compareTo(BigDecimal.ONE) < 0) price = BigDecimal.ONE;
            prices.add(price);
        }
        return prices;
    }

    /** Simple LCG — fast, side-effect-free, and easy to reason about in tests. */
    private static long lcgNext(long state) {
        return state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
    }
}
