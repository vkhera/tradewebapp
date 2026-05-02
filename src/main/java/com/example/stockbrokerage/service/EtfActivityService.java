package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.EtfChangeDto;
import com.example.stockbrokerage.dto.TrendPrediction.TrendDirection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Fetches ETF holdings changes from the local ETF dashboard service
 * (http://localhost:8000) and exposes signal calculations for BUZZ, HDGE, MMTM.
 *
 * <ul>
 *   <li>BUZZ (Social-buzz ETF):  Added → bullish, Removed → bearish</li>
 *   <li>HDGE (Short-bet ETF):    Added → bearish (fund is shorting it), Removed → bullish</li>
 *   <li>MMTM (Momentum ETF):     Added → bullish, Removed → bearish</li>
 * </ul>
 */
@Service
@Slf4j
public class EtfActivityService {

    private static final Set<String> TARGET_ETFS = Set.of("BUZZ", "HDGE", "MMTM");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${app.etf-dashboard.url:http://localhost:8000}")
    private String dashboardBaseUrl;

    @Value("${app.etf-dashboard.fallback-url:http://host.docker.internal:8000}")
    private String dashboardFallbackUrl;

    @Value("${app.etf-dashboard.lookback-days:7}")
    private int lookbackDays;

    @Value("${app.etf-dashboard.max-retries:2}")
    private int maxRetries;

    @Value("${app.etf-dashboard.retry-delay-ms:250}")
    private long retryDelayMs;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EtfActivityService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * Returns all ETF changes (BUZZ/HDGE/MMTM) from the last {@code lookbackDays} days.
     */
    public List<EtfChangeDto> getAllRecentChanges() {
        return fetchAndParse(null);
    }

    /**
     * Returns ETF changes for a specific stock symbol (case-insensitive) across BUZZ/HDGE/MMTM.
     */
    public List<EtfChangeDto> getChangesForSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }
        String upper = symbol.trim().toUpperCase();
        return fetchAndParse(upper);
    }

    /**
     * Computes a composite ETF signal for a given stock symbol.
     *
     * <p>Scoring per matching change in the last 7 days:
     * <ul>
     *   <li>BUZZ Add  → +1 (social buzz is bullish)</li>
     *   <li>BUZZ Remove → -1</li>
     *   <li>HDGE Add  → -1 (fund is shorting – bearish)</li>
     *   <li>HDGE Remove → +1 (no longer a short target)</li>
     *   <li>MMTM Add  → +1 (has momentum – bullish)</li>
     *   <li>MMTM Remove → -1</li>
     * </ul>
     */
    public TrendDirection getEtfSignal(String symbol) {
        try {
            List<EtfChangeDto> changes = getChangesForSymbol(symbol);
            int score = 0;
            for (EtfChangeDto c : changes) {
                boolean isAdd = "Added".equalsIgnoreCase(c.action());
                score += switch (c.etfName().toUpperCase()) {
                    case "BUZZ" -> isAdd ? +1 : -1;
                    case "HDGE" -> isAdd ? -1 : +1;  // inverse – being shorted = bearish
                    case "MMTM" -> isAdd ? +1 : -1;
                    default     -> 0;
                };
            }
            if (score > 0) return TrendDirection.UPTREND;
            if (score < 0) return TrendDirection.DOWNTREND;
        } catch (Exception e) {
            log.debug("ETF signal unavailable for {}: {}", symbol, e.getMessage());
        }
        return TrendDirection.SIDEWAYS;
    }

    // ── private ──────────────────────────────────────────────────────────────

    private List<EtfChangeDto> fetchAndParse(String filterSymbol) {
        int attempts = Math.max(0, maxRetries) + 1;
        List<String> urls = resolveDashboardUrls();
        Exception lastException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            for (String baseUrl : urls) {
                try {
                    String url = baseUrl + "/api/etf-performance";
                    String json = restTemplate.getForObject(url, String.class);
                    if (json == null || json.isBlank()) return List.of();

                    JsonNode root = objectMapper.readTree(json);
                    JsonNode holdingsChanges = root.path("holdings_changes");
                    if (holdingsChanges.isMissingNode() || holdingsChanges.isNull()) {
                        log.debug("No holdings_changes node in ETF dashboard response");
                        return List.of();
                    }

                    LocalDate cutoff = LocalDate.now().minusDays(lookbackDays);
                    List<EtfChangeDto> results = new ArrayList<>();

                    holdingsChanges.fields().forEachRemaining(entry -> {
                        String etfName = entry.getKey().toUpperCase();
                        if (!TARGET_ETFS.contains(etfName)) return;

                        JsonNode changesArray = entry.getValue();
                        if (!changesArray.isArray()) return;

                        for (JsonNode node : changesArray) {
                            String rawDate = node.path("date").asText(null);
                            if (rawDate == null) continue;
                            try {
                                LocalDate changeDate = LocalDate.parse(rawDate, DATE_FMT);
                                if (changeDate.isBefore(cutoff)) continue;

                                String ticker = cleanTicker(node.path("ticker").asText(""));
                                if (ticker.isBlank()) continue;
                                if (filterSymbol != null && !filterSymbol.equals(ticker)) continue;

                                String action = node.path("action").asText("");
                                if (!action.equalsIgnoreCase("Added") && !action.equalsIgnoreCase("Removed")) continue;

                                Double priceAt  = parsePrice(node.path("price_at_change").asText(null));
                                Double priceCur = parsePrice(node.path("current_price").asText(null));
                                String result   = node.path("result").asText(null);
                                if (result != null && result.isBlank()) result = null;

                                results.add(new EtfChangeDto(etfName, ticker, action, changeDate, priceAt, priceCur, result));
                            } catch (Exception ex) {
                                log.trace("Skipping ETF change row: {}", ex.getMessage());
                            }
                        }
                    });

                    return results;
                } catch (Exception e) {
                    lastException = e;
                    log.warn(
                        "ETF dashboard fetch attempt {}/{} failed via {}: {}",
                        attempt,
                        attempts,
                        baseUrl,
                        conciseError(e)
                    );
                }
            }

            if (attempt < attempts && retryDelayMs > 0) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.warn("Could not fetch ETF activity from dashboard after {} attempt(s): {}",
            attempts, lastException == null ? "unknown error" : conciseError(lastException));
        return List.of();
    }

    private List<String> resolveDashboardUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(dashboardBaseUrl);
        if (dashboardBaseUrl != null && dashboardBaseUrl.contains("localhost")
            && dashboardFallbackUrl != null && !dashboardFallbackUrl.isBlank()
            && !dashboardFallbackUrl.equalsIgnoreCase(dashboardBaseUrl)) {
            urls.add(dashboardFallbackUrl);
        }
        return urls;
    }

    private String conciseError(Exception ex) {
        if (ex instanceof HttpStatusCodeException httpEx) {
            String body = httpEx.getResponseBodyAsString();
            if (body != null && body.length() > 180) {
                body = body.substring(0, 180);
            }
            return httpEx.getStatusCode() + (body == null || body.isBlank() ? "" : " body=" + body);
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    /** Strips trailing " US" suffixes (e.g. "AAPL US" → "AAPL"). */
    private String cleanTicker(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+US$", "").trim().toUpperCase();
    }

    private Double parsePrice(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("N/A")) return null;
        try {
            return Double.parseDouble(raw.replace(",", "").replace("$", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
