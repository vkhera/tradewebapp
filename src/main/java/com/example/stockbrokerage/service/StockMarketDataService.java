package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.YahooFinanceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Provides historical 5-minute stock price data.
 * <p>
 * Data is sourced from Yahoo Finance via {@link YahooFinanceClient} and cached locally as CSV
 * to avoid redundant network calls. In the {@code test} profile the client is replaced by
 * {@link com.example.stockbrokerage.client.MockYahooFinanceClient}, which returns
 * deterministic stub data with no network access.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockMarketDataService {

    private static final String DATA_DIR = "stock_predictions";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final YahooFinanceClient yahooFinanceClient;

    /**
     * Returns the last N 5-minute bars of closing prices for the symbol.
     * Ordered oldest -> newest.
     */
    public List<BigDecimal> getPrices(String symbol, int bars) {
        ensureDataDir();

        // Try to load from cache first (less than 5 minutes old)
        List<BigDecimal> cached = loadFromCsvCache(symbol, bars);
        if (!cached.isEmpty()) {
            log.debug("Loaded {} 5-min prices for {} from cache", cached.size(), symbol);
            return cached;
        }

        // Fetch fresh from Yahoo Finance (real or mock, depending on active profile)
        List<BigDecimal> prices = yahooFinanceClient.getHistoricalPrices(symbol);

        if (prices.isEmpty()) {
            log.warn("Yahoo Finance returned no data for {}, using fallback", symbol);
            prices = generateRealisticFallback(symbol, bars);
        }

        // Persist to CSV cache
        if (!prices.isEmpty()) {
            saveToCsvCache(symbol, prices);
        }

        return prices.size() > bars ? prices.subList(prices.size() - bars, prices.size()) : prices;
    }

    /**
     * Returns the current (most recent) price for the symbol.
     */
    public BigDecimal getCurrentPrice(String symbol) {
        List<BigDecimal> prices = getPrices(symbol, 1);
        if (!prices.isEmpty()) {
            return prices.getLast();
        }
        return BigDecimal.valueOf(100); // ultimate fallback
    }

    // -------------------------------------------------------------------------
    // CSV Cache
    // -------------------------------------------------------------------------

    private List<BigDecimal> loadFromCsvCache(String symbol, int bars) {
        Path csvPath = Paths.get(DATA_DIR, symbol + "_prices.csv");
        if (!Files.exists(csvPath)) {
            return List.of();
        }

        try {
            // Check cache freshness - only use if < 5 min old (matches bar interval)
            FileTime lastModified = Files.getLastModifiedTime(csvPath);
            Instant cutoff = Instant.now().minusSeconds(300); // 5 minutes
            if (lastModified.toInstant().isBefore(cutoff)) {
                return List.of(); // stale, force refresh
            }

            List<BigDecimal> prices = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
                String line;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    if (header) { header = false; continue; }
                    String[] parts = line.split(",");
                    if (parts.length >= 2 && !parts[1].isBlank()) {
                        prices.add(new BigDecimal(parts[1].trim()));
                    }
                }
            }
            return prices.size() > bars ? prices.subList(prices.size() - bars, prices.size()) : prices;

        } catch (Exception e) {
            log.warn("Error loading CSV cache for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private void saveToCsvCache(String symbol, List<BigDecimal> prices) {
        Path csvPath = Paths.get(DATA_DIR, symbol + "_prices.csv");
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvPath.toFile()))) {
            writer.println("Timestamp,ClosePrice");
            // Generate approximate 5-min timestamps going back
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
            int floorMin = now.getMinute() - (now.getMinute() % 5);
            now = now.withMinute(floorMin);
            for (int i = 0; i < prices.size(); i++) {
                LocalDateTime ts = now.minusMinutes(5L * (prices.size() - 1 - i));
                writer.printf("%s,%s%n", ts.format(TS_FMT), prices.get(i).toPlainString());
            }
            log.debug("Saved {} 5-min bars to CSV cache for {}", prices.size(), symbol);
        } catch (IOException e) {
            log.error("Error saving price CSV cache for {}: {}", symbol, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Realistic fallback: random walk from last known price
    // -------------------------------------------------------------------------

    private List<BigDecimal> generateRealisticFallback(String symbol, int bars) {
        log.warn("Generating synthetic 5-min price data for {} (API unavailable)", symbol);
        Random rng = new Random(symbol.hashCode());
        BigDecimal price = BigDecimal.valueOf(50 + rng.nextInt(250));
        double volatility = 0.001 + rng.nextDouble() * 0.002; // 0.1-0.3% per 5-min bar

        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < bars; i++) {
            double change = (rng.nextGaussian() * volatility);
            price = price.multiply(BigDecimal.valueOf(1 + change)).setScale(4, RoundingMode.HALF_UP);
            if (price.compareTo(BigDecimal.ONE) < 0) price = BigDecimal.valueOf(1.0);
            prices.add(price);
        }
        return prices;
    }

    private void ensureDataDir() {
        Path dir = Paths.get(DATA_DIR);
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                log.error("Cannot create data directory", e);
            }
        }
    }
}
