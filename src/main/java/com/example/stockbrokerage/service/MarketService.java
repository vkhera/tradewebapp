package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.YahooFinanceClient;
import com.example.stockbrokerage.dto.MarketIndexQuote;
import com.example.stockbrokerage.dto.MarketStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketService {

    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    /** Indices to track: {Yahoo symbol, display name}. */
    private static final List<String[]> INDICES = List.of(
            new String[]{"^GSPC",  "S&P 500"},
            new String[]{"^DJI",   "Dow Jones"},
            new String[]{"^IXIC",  "Nasdaq"},
            new String[]{"GC=F",   "Gold"},
            new String[]{"^RUT",   "Russell 2K"}
    );

    /** 1-minute cache so every portfolio page-load doesn't hammer Yahoo Finance. */
    private static final long INDICES_CACHE_TTL_MS = 60_000L;
    private volatile List<MarketIndexQuote> indicesCache;
    private volatile long indicesCacheExpiresAt = 0;

    private final YahooFinanceClient yahooFinanceClient;

    // -------------------------------------------------------------------------
    // Market status
    // -------------------------------------------------------------------------

    /**
     * Returns the current NYSE/NASDAQ market session status based on Eastern Time.
     * Holiday detection is not performed — weekends and US market holidays are
     * treated the same as any other day for the session-time logic.
     */
    public MarketStatusResponse getMarketStatus() {
        ZonedDateTime now = ZonedDateTime.now(EASTERN);
        LocalTime time = now.toLocalTime();

        LocalTime preMarketStart  = LocalTime.of(4, 0);
        LocalTime marketOpen      = LocalTime.of(9, 30);
        LocalTime marketClose     = LocalTime.of(16, 0);
        LocalTime postMarketEnd   = LocalTime.of(20, 0);

        String status, statusLabel;
        boolean isRegularOpen;

        if (time.isBefore(preMarketStart) || !time.isBefore(postMarketEnd)) {
            status = "CLOSED";        statusLabel = "Closed";       isRegularOpen = false;
        } else if (time.isBefore(marketOpen)) {
            status = "PRE_MARKET";   statusLabel = "Pre-Market";   isRegularOpen = false;
        } else if (time.isBefore(marketClose)) {
            status = "OPEN";          statusLabel = "Open";          isRegularOpen = true;
        } else {
            status = "POST_MARKET";  statusLabel = "Post-Market";  isRegularOpen = false;
        }

        return new MarketStatusResponse(TIME_FMT.format(now), status, statusLabel, isRegularOpen);
    }

    // -------------------------------------------------------------------------
    // Market indices
    // -------------------------------------------------------------------------

    /** Returns index snapshots, served from a 1-minute in-memory cache. */
    public List<MarketIndexQuote> getIndices() {
        long now = System.currentTimeMillis();
        if (indicesCache != null && now < indicesCacheExpiresAt) {
            return indicesCache;
        }

        List<MarketIndexQuote> fresh = INDICES.parallelStream()
                .map(this::fetchIndexQuote)
                .collect(Collectors.toList());

        indicesCache = fresh;
        indicesCacheExpiresAt = now + INDICES_CACHE_TTL_MS;
        return fresh;
    }

    @SuppressWarnings("unchecked")
    private MarketIndexQuote fetchIndexQuote(String[] symbolAndName) {
        String symbol = symbolAndName[0];
        String name   = symbolAndName[1];
        try {
            Map<String, Object> meta = yahooFinanceClient.getChartMeta(symbol);
            if (meta == null || meta.isEmpty()) {
                return new MarketIndexQuote(symbol, name, null, null, null, null, null, null);
            }

            BigDecimal price = extractDecimal(meta, "regularMarketPrice");

            // Prefer chartPreviousClose (most reliable for day-change), fall back to previousClose
            BigDecimal prevClose = extractDecimal(meta, "chartPreviousClose");
            if (prevClose == null) prevClose = extractDecimal(meta, "regularMarketPreviousClose");

            BigDecimal change    = null;
            BigDecimal changePct = null;
            if (price != null && prevClose != null && prevClose.compareTo(BigDecimal.ZERO) != 0) {
                change    = price.subtract(prevClose).setScale(4, RoundingMode.HALF_UP);
                changePct = change.divide(prevClose, 6, RoundingMode.HALF_UP)
                                  .multiply(BigDecimal.valueOf(100))
                                  .setScale(4, RoundingMode.HALF_UP);
            }

            // postMarketPrice is not surfaced in meta by Yahoo Finance; delegate to the
            // dedicated client method which reads it from 1-minute bar data.
            BigDecimal postPrice = yahooFinanceClient.getPostMarketPrice(symbol);
            BigDecimal postChange    = null;
            BigDecimal postChangePct = null;
            if (postPrice != null && price != null && price.compareTo(BigDecimal.ZERO) != 0) {
                postChange    = postPrice.subtract(price).setScale(4, RoundingMode.HALF_UP);
                postChangePct = postChange.divide(price, 6, RoundingMode.HALF_UP)
                                          .multiply(BigDecimal.valueOf(100))
                                          .setScale(4, RoundingMode.HALF_UP);
            }

            return new MarketIndexQuote(symbol, name, price, change, changePct,
                                        postPrice, postChange, postChangePct);
        } catch (Exception e) {
            log.warn("Failed to fetch index quote for {}: {}", symbol, e.getMessage());
            return new MarketIndexQuote(symbol, name, null, null, null, null, null, null);
        }
    }

    private static BigDecimal extractDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        double d = ((Number) val).doubleValue();
        if (d == 0.0) return null;
        return BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP);
    }
}
