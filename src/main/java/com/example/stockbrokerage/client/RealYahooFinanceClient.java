package com.example.stockbrokerage.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.stockbrokerage.dto.DailyBar;
import com.example.stockbrokerage.dto.NewsItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live implementation of {@link YahooFinanceClient} that calls the public Yahoo Finance
 * endpoints.  Active in every Spring profile <em>except</em> {@code test}.
 */
@Component
@Profile("!test")
@Slf4j
public class RealYahooFinanceClient implements YahooFinanceClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Short-lived in-memory price cache: avoids redundant Yahoo HTTP calls within a 5-minute window. */
    private record CachedPrice(BigDecimal price, long expiresAt) {}
    private static final long PRICE_CACHE_TTL_MS = 5 * 60 * 1_000L; // 5 minutes
    private final ConcurrentHashMap<String, CachedPrice> priceCache = new ConcurrentHashMap<>();

    /** Post-market price cache: stores after-hours price (null when unavailable). */
    private record CachedPostMarket(BigDecimal price, long expiresAt) {}
    private final ConcurrentHashMap<String, CachedPostMarket> postMarketCache = new ConcurrentHashMap<>();

    public RealYahooFinanceClient() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("User-Agent", USER_AGENT);
            return execution.execute(request, body);
        });
    }

    // -------------------------------------------------------------------------
    // YahooFinanceClient implementation
    // -------------------------------------------------------------------------

    @Override
    public BigDecimal getCurrentPrice(String symbol) {
        // Serve from in-memory cache if still fresh (eliminates redundant HTTP calls per portfolio load)
        CachedPrice cached = priceCache.get(symbol);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) {
            log.debug("Price cache hit for {}: {}", symbol, cached.price());
            return cached.price();
        }

        BigDecimal price = tryQuoteEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            priceCache.put(symbol, new CachedPrice(price, System.currentTimeMillis() + PRICE_CACHE_TTL_MS));
            return price;
        }

        price = tryChartEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            priceCache.put(symbol, new CachedPrice(price, System.currentTimeMillis() + PRICE_CACHE_TTL_MS));
            return price;
        }

        price = tryV6QuoteEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            priceCache.put(symbol, new CachedPrice(price, System.currentTimeMillis() + PRICE_CACHE_TTL_MS));
            return price;
        }

        // 4th fallback: query2 CDN v8/chart — different CDN node, often succeeds when query1 rejects with 401
        price = tryQuery2ChartEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            priceCache.put(symbol, new CachedPrice(price, System.currentTimeMillis() + PRICE_CACHE_TTL_MS));
            return price;
        }

        log.warn("All Yahoo Finance price endpoints failed for symbol: {}", symbol);
        return BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getPostMarketPrice(String symbol) {
        CachedPostMarket cached = postMarketCache.get(symbol);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) {
            log.debug("Post-market cache hit for {}: {}", symbol, cached.price());
            return cached.price();
        }

        BigDecimal postPrice = fetchPostMarketPrice(symbol);
        // Only cache non-null values — a null result means post-market data is not yet
        // available (e.g. right at 4 PM close).  Caching null would suppress the price
        // for a full 5 minutes even after Yahoo Finance starts reporting it.
        if (postPrice != null) {
            postMarketCache.put(symbol, new CachedPostMarket(postPrice, System.currentTimeMillis() + PRICE_CACHE_TTL_MS));
        }
        return postPrice;
    }

    private BigDecimal fetchPostMarketPrice(String symbol) {
        // v8/chart (query1) — primary; v7/quote is blocked (401)
        BigDecimal price = extractPostMarketFromV8Chart(symbol, "query1");
        if (price != null) return price;
        // v8/chart (query2) — fallback CDN
        return extractPostMarketFromV8Chart(symbol, "query2");
    }

    @SuppressWarnings("unchecked")
    private BigDecimal extractPostMarketFromV8Chart(String symbol, String queryHost) {
        try {
            // Use 1-minute bars so we get the full intraday bar array including post-market entries.
            // Yahoo Finance v8 chart meta does NOT surface postMarketPrice as a top-level field;
            // the actual extended-hours price lives in the per-minute bar data after the regular
            // session close (meta.currentTradingPeriod.post.start).
            String url = "https://%s.finance.yahoo.com/v8/finance/chart/%s?interval=1m&range=1d&includePrePost=true"
                    .formatted(queryHost, symbol);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return null;
            Map<String, Object> chart = (Map<String, Object>) response.get("chart");
            if (chart == null) return null;
            var results = (java.util.List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return null;
            Map<String, Object> result = results.getFirst();
            Map<String, Object> meta = (Map<String, Object>) result.get("meta");
            if (meta == null) return null;

            // Determine the post-market window from meta.currentTradingPeriod.post
            Map<String, Object> ctp = (Map<String, Object>) meta.get("currentTradingPeriod");
            if (ctp == null) return null;
            Map<String, Object> postPeriod = (Map<String, Object>) ctp.get("post");
            if (postPeriod == null) return null;
            long postStart = ((Number) postPeriod.get("start")).longValue();
            long postEnd   = ((Number) postPeriod.get("end")).longValue();

            var timestamps = (java.util.List<Number>) result.get("timestamp");
            if (timestamps == null || timestamps.isEmpty()) return null;
            Map<String, Object> indicators = (Map<String, Object>) result.get("indicators");
            if (indicators == null) return null;
            var quoteList = (java.util.List<Map<String, Object>>) indicators.get("quote");
            if (quoteList == null || quoteList.isEmpty()) return null;
            var closes = (java.util.List<Number>) quoteList.getFirst().get("close");
            if (closes == null || closes.isEmpty()) return null;

            // Walk backwards to find the most recent non-null close within the post-market window
            int n = Math.min(timestamps.size(), closes.size());
            for (int i = n - 1; i >= 0; i--) {
                long ts = timestamps.get(i).longValue();
                if (ts > postEnd) continue;    // beyond post-market window
                if (ts < postStart) break;     // passed back before post-market started
                Number closeNum = closes.get(i);
                if (closeNum == null) continue;
                double price = closeNum.doubleValue();
                if (price > 0.0) {
                    log.debug("Post-market price for {} via v8/chart {}: {} (ts={})", symbol, queryHost, price, ts);
                    return BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP);
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Post-market price fetch failed for {} via {}: {}", symbol, queryHost, e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> getQuote(String symbol) {
        try {
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d"
                    .formatted(symbol);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : Map.of("error", "Empty response");
        } catch (Exception e) {
            log.error("Error fetching quote for symbol: {}", symbol, e);
            return Map.of("error", "Failed to fetch quote");
        }
    }

    @Override
    public Map<String, Object> getChartMeta(String symbol) {
        // URL-encode special characters used in index symbols (^ → %5E, = → %3D).
        // RestTemplate.getForObject(String, Class) passes the literal string to URI.create()
        // which does NOT re-encode already-percent-encoded sequences, so this is safe.
        String encoded = symbol.replace("^", "%5E").replace("=", "%3D");
        Map<String, Object> meta = extractFullMeta(encoded, "query1");
        if (meta == null) meta = extractFullMeta(encoded, "query2");
        return meta != null ? meta : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFullMeta(String encodedSymbol, String queryHost) {
        try {
            String url = "https://%s.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d&includePrePost=true"
                    .formatted(queryHost, encodedSymbol);
            // Use URI.create() so RestTemplate sends the pre-encoded symbol (e.g. %5E for ^)
            // without double-encoding the percent sign to %25.
            Map<String, Object> response = restTemplate.getForObject(URI.create(url), Map.class);
            if (response == null) return null;
            Map<String, Object> chart = (Map<String, Object>) response.get("chart");
            if (chart == null) return null;
            var results = (java.util.List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return null;
            return (Map<String, Object>) results.getFirst().get("meta");
        } catch (Exception e) {
            log.debug("extractFullMeta failed for {} via {}: {}", encodedSymbol, queryHost, e.getMessage());
            return null;
        }
    }

    @Override
    public List<BigDecimal> getHistoricalPrices(String symbol) {
        // Try query1 first (primary CDN); fall back to query2 if rate-limited or crumb expired
        List<BigDecimal> prices = fetchChart5mPrices(symbol, "query1");
        if (!prices.isEmpty()) return prices;
        log.info("query1 5-min history failed for {} — retrying via query2.finance.yahoo.com", symbol);
        return fetchChart5mPrices(symbol, "query2");
    }

    private List<BigDecimal> fetchChart5mPrices(String symbol, String queryHost) {
        try {
            // 5-min bars, 60-day range — ~4 680 bars (free, no API key)
            String url = "https://%s.finance.yahoo.com/v8/finance/chart/%s?interval=5m&range=60d"
                    .formatted(queryHost, symbol);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "application/json");
            headers.set("Accept-Language", "en-US,en;q=0.9");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("Yahoo Finance chart endpoint ({}) returned {} for {}", queryHost, response.getStatusCode(), symbol);
                return List.of();
            }

            return parseHistoricalResponse(response.getBody(), symbol);

        } catch (Exception e) {
            log.warn("Failed to fetch historical prices from {}.finance.yahoo.com for {}: {}", queryHost, symbol, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<DailyBar> getDailyBars(String symbol, int days) {
        // Try query1 first; fall back to query2 CDN on failure
        List<DailyBar> bars = fetchDailyBarsFromHost(symbol, days, "query1");
        if (!bars.isEmpty()) return bars;
        log.info("query1 daily bars failed for {} — retrying via query2.finance.yahoo.com", symbol);
        return fetchDailyBarsFromHost(symbol, days, "query2");
    }

    @Override
    public List<NewsItem> getRecentNews(String symbol, int lookbackDays) {
        List<NewsItem> news = fetchNewsFromHost(symbol, lookbackDays, "query1");
        if (!news.isEmpty()) return news;
        log.info("query1 news endpoint failed for {} — retrying via query2.finance.yahoo.com", symbol);
        return fetchNewsFromHost(symbol, lookbackDays, "query2");
    }

    private List<NewsItem> fetchNewsFromHost(String symbol, int lookbackDays, String queryHost) {
        try {
            String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
            String url = "https://%s.finance.yahoo.com/v1/finance/search?q=%s&quotesCount=0&newsCount=30"
                .formatted(queryHost, encodedSymbol);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "application/json");
            headers.set("Accept-Language", "en-US,en;q=0.9");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response =
                restTemplate.exchange(URI.create(url), HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("Yahoo Finance news endpoint ({}) returned {} for {}", queryHost, response.getStatusCode(), symbol);
                return List.of();
            }

            return parseNewsResponse(response.getBody(), symbol, lookbackDays);
        } catch (Exception e) {
            log.warn("Failed to fetch news from {}.finance.yahoo.com for {}: {}", queryHost, symbol, e.getMessage());
            return List.of();
        }
    }

    private List<NewsItem> parseNewsResponse(String json, String symbol, int lookbackDays) {
        List<NewsItem> items = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode newsArray = root.path("news");
            if (!newsArray.isArray() || newsArray.isEmpty()) {
                return items;
            }

            LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, lookbackDays));
            for (JsonNode newsNode : newsArray) {
                long publishEpoch = newsNode.path("providerPublishTime").asLong(0L);
                LocalDateTime publishedAt = publishEpoch > 0
                    ? Instant.ofEpochSecond(publishEpoch).atZone(ZoneId.of("America/New_York")).toLocalDateTime()
                    : LocalDateTime.now();

                if (publishedAt.isBefore(cutoff)) {
                    continue;
                }

                String title = newsNode.path("title").asText("").trim();
                if (title.isBlank()) {
                    continue;
                }

                String link = newsNode.path("link").asText("").trim();
                String uuid = newsNode.path("uuid").asText("").trim();
                String summary = newsNode.path("summary").asText("").trim();
                String publisher = newsNode.path("publisher").asText("").trim();

                String externalId = !uuid.isBlank()
                    ? uuid
                    : Integer.toHexString(Objects.hash(symbol, title, link, publishEpoch));

                items.add(new NewsItem(symbol, externalId, title, summary, publisher, link, publishedAt));
            }
        } catch (Exception e) {
            log.warn("Error parsing Yahoo news response for {}: {}", symbol, e.getMessage());
        }
        return items;
    }

    private List<DailyBar> fetchDailyBarsFromHost(String symbol, int days, String queryHost) {
        try {
            // Use 6mo range for up to ~126 trading days of daily OHLCV data
            String range = days <= 60 ? "3mo" : "6mo";
            String url = "https://%s.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=%s"
                    .formatted(queryHost, symbol, range);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "application/json");
            headers.set("Accept-Language", "en-US,en;q=0.9");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("Yahoo Finance daily chart ({}) returned {} for {}", queryHost, response.getStatusCode(), symbol);
                return List.of();
            }

            return parseDailyBarsResponse(response.getBody(), symbol);

        } catch (Exception e) {
            log.warn("Failed to fetch daily bars from {}.finance.yahoo.com for {}: {}", queryHost, symbol, e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — price endpoints
    // -------------------------------------------------------------------------

    private BigDecimal tryQuery2ChartEndpoint(String symbol) {
        try {
            String url = "https://query2.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d"
                    .formatted(symbol);
            log.info("Trying query2/v8/chart endpoint for symbol: {}", symbol);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("chart")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> chart = (Map<String, Object>) response.get("chart");
                if (chart.containsKey("result")) {
                    @SuppressWarnings("unchecked")
                    var results = (java.util.List<Map<String, Object>>) chart.get("result");
                    if (!results.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meta = (Map<String, Object>) results.getFirst().get("meta");
                        if (meta != null && meta.containsKey("regularMarketPrice")) {
                            double price = ((Number) meta.get("regularMarketPrice")).doubleValue();
                            log.info("\u2713 query2/v8/chart succeeded for {}: {}", symbol, price);
                            return BigDecimal.valueOf(price);
                        }
                    }
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("\u2717 query2/v8/chart rate limited for {}", symbol);
        } catch (Exception e) {
            log.warn("\u2717 query2/v8/chart failed for {}: {}", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal tryQuoteEndpoint(String symbol) {
        try {
            String url = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=%s".formatted(symbol);
            log.info("Trying v7/quote endpoint for symbol: {}", symbol);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("quoteResponse")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> quoteResponse = (Map<String, Object>) response.get("quoteResponse");
                if (quoteResponse.containsKey("result")) {
                    @SuppressWarnings("unchecked")
                    var results = (java.util.List<Map<String, Object>>) quoteResponse.get("result");
                    if (!results.isEmpty()) {
                        Map<String, Object> quote = results.getFirst();
                        Object priceObj = firstNonNull(quote, "regularMarketPrice", "ask", "bid");
                        if (priceObj != null) {
                            double price = ((Number) priceObj).doubleValue();
                            log.info("✓ v7/quote succeeded for {}: {}", symbol, price);
                            return BigDecimal.valueOf(price);
                        }
                    }
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("✗ v7/quote rate limited for {}", symbol);
        } catch (Exception e) {
            log.warn("✗ v7/quote failed for {}: {}", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal tryChartEndpoint(String symbol) {
        try {
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d"
                    .formatted(symbol);
            log.info("Trying v8/chart endpoint for symbol: {}", symbol);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("chart")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> chart = (Map<String, Object>) response.get("chart");
                if (chart.containsKey("result")) {
                    @SuppressWarnings("unchecked")
                    var results = (java.util.List<Map<String, Object>>) chart.get("result");
                    if (!results.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meta = (Map<String, Object>) results.getFirst().get("meta");
                        if (meta != null && meta.containsKey("regularMarketPrice")) {
                            double price = ((Number) meta.get("regularMarketPrice")).doubleValue();
                            log.info("✓ v8/chart succeeded for {}: {}", symbol, price);
                            return BigDecimal.valueOf(price);
                        }
                    }
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("✗ v8/chart rate limited for {}", symbol);
        } catch (Exception e) {
            log.warn("✗ v8/chart failed for {}: {}", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal tryV6QuoteEndpoint(String symbol) {
        try {
            String url = "https://query2.finance.yahoo.com/v6/finance/quote?symbols=%s".formatted(symbol);
            log.info("Trying v6/quote endpoint for symbol: {}", symbol);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("quoteResponse")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> quoteResponse = (Map<String, Object>) response.get("quoteResponse");
                if (quoteResponse.containsKey("result")) {
                    @SuppressWarnings("unchecked")
                    var results = (java.util.List<Map<String, Object>>) quoteResponse.get("result");
                    if (!results.isEmpty()) {
                        Map<String, Object> quote = results.getFirst();
                        Object priceObj = firstNonNull(quote, "regularMarketPrice", "ask", "bid");
                        if (priceObj != null) {
                            double price = ((Number) priceObj).doubleValue();
                            log.info("✓ v6/quote succeeded for {}: {}", symbol, price);
                            return BigDecimal.valueOf(price);
                        }
                    }
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("✗ v6/quote rate limited for {}", symbol);
        } catch (Exception e) {
            log.warn("✗ v6/quote failed for {}: {}", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    // -------------------------------------------------------------------------
    // Private helpers — historical data
    // -------------------------------------------------------------------------

    private List<DailyBar> parseDailyBarsResponse(String json, String symbol) {
        List<DailyBar> bars = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode result = root.path("chart").path("result");
            if (result.isEmpty() || !result.isArray()) return bars;

            JsonNode firstResult = result.get(0);
            JsonNode timestamps  = firstResult.path("timestamp");
            JsonNode quote = firstResult.path("indicators").path("quote").get(0);

            if (!timestamps.isArray() || quote == null) return bars;

            JsonNode opens   = quote.path("open");
            JsonNode highs   = quote.path("high");
            JsonNode lows    = quote.path("low");
            JsonNode closes  = quote.path("close");
            JsonNode volumes = quote.path("volume");

            for (int i = 0; i < timestamps.size(); i++) {
                JsonNode closeNode = closes.get(i);
                if (closeNode == null || closeNode.isNull()) continue;

                long epochSec = timestamps.get(i).asLong();
                java.time.LocalDate date = Instant.ofEpochSecond(epochSec)
                        .atZone(ZoneId.of("America/New_York")).toLocalDate();

                BigDecimal open  = safeDecimal(opens,   i);
                BigDecimal high  = safeDecimal(highs,   i);
                BigDecimal low   = safeDecimal(lows,    i);
                BigDecimal close = safeDecimal(closes,  i);
                long volume = (volumes != null && !volumes.get(i).isNull())
                        ? volumes.get(i).asLong() : 0L;

                bars.add(new DailyBar(date, open, high, low, close, volume));
            }
            log.info("Fetched {} daily bars for {} from Yahoo Finance", bars.size(), symbol);
        } catch (Exception e) {
            log.error("Error parsing daily bars for {}: {}", symbol, e.getMessage());
        }
        return bars;
    }

    private BigDecimal safeDecimal(JsonNode array, int index) {
        if (array == null) return BigDecimal.ZERO;
        JsonNode node = array.get(index);
        if (node == null || node.isNull()) return BigDecimal.ZERO;
        return BigDecimal.valueOf(node.asDouble()).setScale(4, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> parseHistoricalResponse(String json, String symbol) {
        List<BigDecimal> prices = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode result = root.path("chart").path("result");
            if (result.isEmpty() || !result.isArray()) return prices;

            JsonNode closePrices = result.get(0)
                    .path("indicators").path("quote").get(0).path("close");

            if (closePrices.isArray()) {
                for (JsonNode node : closePrices) {
                    if (!node.isNull()) {
                        prices.add(BigDecimal.valueOf(node.asDouble())
                                .setScale(4, RoundingMode.HALF_UP));
                    }
                }
            }
            log.info("Fetched {} 5-min bars for {} from Yahoo Finance", prices.size(), symbol);
        } catch (Exception e) {
            log.error("Error parsing Yahoo Finance response for {}: {}", symbol, e.getMessage());
        }
        return prices;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private Object firstNonNull(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) return value;
        }
        return null;
    }
}
