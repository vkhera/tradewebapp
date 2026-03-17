package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.OptionsSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and parses options-chain data for a given symbol from the public Yahoo Finance
 * options endpoint ({@code /v7/finance/options/{symbol}}).  No API key is required.
 *
 * <h3>Computed signals (front-month expiry only)</h3>
 * <ul>
 *   <li><b>ATM Implied Volatility</b> – IV of the call contract with strike
 *       closest to the current market price.  High IV indicates elevated
 *       uncertainty and warrants wider stop-loss bands.</li>
 *   <li><b>Put/Call Ratio (OI)</b> – total put open-interest divided by total
 *       call open-interest.  A high PCR (&gt;1.5) signals bearish hedging extremes
 *       and is a contrarian bullish indicator; a low PCR (&lt;0.7) signals
 *       complacency and is a contrarian bearish indicator.</li>
 *   <li><b>Max Pain</b> – the strike price at which the collective intrinsic
 *       value of all front-month options is minimised for buyers.  Acts as a
 *       short-term price magnet near expiration.</li>
 * </ul>
 *
 * <h3>Caching</h3>
 * Results are cached per symbol for {@value #CACHE_TTL_MINUTES} minutes.
 * Options chains move slowly intraday so this avoids hammering Yahoo Finance.
 *
 * <h3>Resilience</h3>
 * All fetch and parse errors are caught and logged.  Callers receive an
 * {@link OptionsSnapshot#unavailable(String)} value and must check
 * {@link OptionsSnapshot#dataAvailable()} before using signal fields.
 */
@Service
@Slf4j
public class OptionsDataService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String OPTIONS_URL =
            "https://query1.finance.yahoo.com/v7/finance/options/%s";

    static final int CACHE_TTL_MINUTES = 30;
    private static final long CACHE_TTL_MS = CACHE_TTL_MINUTES * 60 * 1_000L;

    private record CachedSnapshot(OptionsSnapshot snapshot, long expiresAt) {}
    private final ConcurrentHashMap<String, CachedSnapshot> cache = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OptionsDataService() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("User-Agent", USER_AGENT);
            request.getHeaders().add("Accept", "application/json");
            request.getHeaders().add("Accept-Language", "en-US,en;q=0.9");
            return execution.execute(request, body);
        });
        this.objectMapper = new ObjectMapper();
    }

    /** Package-private constructor for unit tests – allows injecting a mock RestTemplate. */
    OptionsDataService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns an {@link OptionsSnapshot} for the given symbol, served from cache
     * when the entry is fresh.  Never throws; returns
     * {@link OptionsSnapshot#unavailable(String)} on any failure.
     */
    public OptionsSnapshot getOptionsSnapshot(String symbol) {
        CachedSnapshot cached = cache.get(symbol);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) {
            log.debug("Options cache hit for {}", symbol);
            return cached.snapshot();
        }
        OptionsSnapshot snapshot = fetchOptionsSnapshot(symbol);
        cache.put(symbol, new CachedSnapshot(snapshot,
                System.currentTimeMillis() + CACHE_TTL_MS));
        return snapshot;
    }

    // ── Fetch & parse ──────────────────────────────────────────────────────────

    private OptionsSnapshot fetchOptionsSnapshot(String symbol) {
        try {
            String url = OPTIONS_URL.formatted(symbol);
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("Options endpoint returned {} for {}", response.getStatusCode(), symbol);
                return OptionsSnapshot.unavailable(symbol);
            }
            return parseOptionsResponse(symbol, response.getBody());

        } catch (Exception e) {
            log.warn("Failed to fetch options data for {}: {}", symbol, e.getMessage());
            return OptionsSnapshot.unavailable(symbol);
        }
    }

    private OptionsSnapshot parseOptionsResponse(String symbol, String json) {
        try {
            JsonNode root   = objectMapper.readTree(json);
            JsonNode result = root.path("optionChain").path("result");
            if (!result.isArray() || result.isEmpty()) {
                log.debug("Empty options result for {}", symbol);
                return OptionsSnapshot.unavailable(symbol);
            }

            JsonNode chain        = result.get(0);
            double currentPrice   = chain.path("quote").path("regularMarketPrice").asDouble(0);
            if (currentPrice <= 0) {
                log.debug("No market price in options chain for {}", symbol);
                return OptionsSnapshot.unavailable(symbol);
            }

            JsonNode optionsArr = chain.path("options");
            if (!optionsArr.isArray() || optionsArr.isEmpty()) {
                return OptionsSnapshot.unavailable(symbol);
            }

            // Front-month expiry (first element)
            JsonNode frontMonth = optionsArr.get(0);
            JsonNode callsNode  = frontMonth.path("calls");
            JsonNode putsNode   = frontMonth.path("puts");
            if (!callsNode.isArray() || !putsNode.isArray()) {
                return OptionsSnapshot.unavailable(symbol);
            }

            double atmIV  = computeAtmIV(callsNode, currentPrice);
            double pcr    = computePCR(callsNode, putsNode);
            double maxPainStrike = computeMaxPain(callsNode, putsNode);

            log.info("Options snapshot for {}: ATM-IV={:.1f}%, PCR={:.2f}, MaxPain={:.2f} (price={:.2f})",
                    symbol, atmIV * 100, pcr, maxPainStrike, currentPrice);

            return new OptionsSnapshot(symbol, atmIV, pcr, maxPainStrike, true);

        } catch (Exception e) {
            log.warn("Error parsing options chain for {}: {}", symbol, e.getMessage());
            return OptionsSnapshot.unavailable(symbol);
        }
    }

    // ── Signal computations ────────────────────────────────────────────────────

    /**
     * ATM IV: takes the call contract with strike nearest to {@code currentPrice}
     * and reads its {@code impliedVolatility} field.
     */
    private double computeAtmIV(JsonNode callsNode, double currentPrice) {
        double bestIV     = 0.0;
        double bestDist   = Double.MAX_VALUE;

        for (JsonNode call : callsNode) {
            double strike = call.path("strike").asDouble(0);
            double iv     = call.path("impliedVolatility").asDouble(0);
            if (strike <= 0 || iv <= 0) continue;

            double dist = Math.abs(strike - currentPrice);
            if (dist < bestDist) {
                bestDist = dist;
                bestIV   = iv;
            }
        }
        return bestIV;
    }

    /**
     * PCR = total front-month put open-interest / total front-month call open-interest.
     * Returns 1.0 (neutral) when call OI is zero to avoid division by zero.
     */
    private double computePCR(JsonNode callsNode, JsonNode putsNode) {
        long callOI = 0;
        long putOI  = 0;
        for (JsonNode call : callsNode) callOI += call.path("openInterest").asLong(0);
        for (JsonNode put  : putsNode)  putOI  += put.path("openInterest").asLong(0);
        return callOI > 0 ? (double) putOI / callOI : 1.0;
    }

    /**
     * Max pain = the strike at which the aggregate intrinsic value of all outstanding
     * options is minimised for option <em>buyers</em>.
     *
     * <p>For each candidate closing price P (each unique strike), the total buyer value is:
     * <ul>
     *   <li>Sum of (P − K) × callOI for all call strikes K &lt; P (ITM calls)</li>
     *   <li>Sum of (K − P) × putOI for all put strikes K &gt; P (ITM puts)</li>
     * </ul>
     * The strike that minimises this total is max pain.
     */
    private double computeMaxPain(JsonNode callsNode, JsonNode putsNode) {
        // Aggregate OI per strike across calls and puts
        Map<Double, long[]> strikeOI = new LinkedHashMap<>(); // [callOI, putOI]

        for (JsonNode call : callsNode) {
            double strike = call.path("strike").asDouble(0);
            long   oi     = call.path("openInterest").asLong(0);
            if (strike <= 0) continue;
            strikeOI.computeIfAbsent(strike, k -> new long[]{0L, 0L})[0] += oi;
        }
        for (JsonNode put : putsNode) {
            double strike = put.path("strike").asDouble(0);
            long   oi     = put.path("openInterest").asLong(0);
            if (strike <= 0) continue;
            strikeOI.computeIfAbsent(strike, k -> new long[]{0L, 0L})[1] += oi;
        }

        if (strikeOI.isEmpty()) return 0.0;

        double minPain        = Double.MAX_VALUE;
        double maxPainStrike  = 0.0;

        for (double candidate : strikeOI.keySet()) {
            double pain = 0.0;
            for (Map.Entry<Double, long[]> entry : strikeOI.entrySet()) {
                double k    = entry.getKey();
                long[] oi   = entry.getValue();
                if (candidate > k) pain += (candidate - k) * oi[0]; // ITM calls
                else if (candidate < k) pain += (k - candidate) * oi[1]; // ITM puts
            }
            if (pain < minPain) {
                minPain       = pain;
                maxPainStrike = candidate;
            }
        }
        return maxPainStrike;
    }
}
