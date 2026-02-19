package com.example.stockbrokerage.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        BigDecimal price = tryQuoteEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) return price;

        price = tryChartEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) return price;

        price = tryV6QuoteEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) return price;

        log.warn("All Yahoo Finance price endpoints failed for symbol: {}", symbol);
        return BigDecimal.ZERO;
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
    public List<BigDecimal> getHistoricalPrices(String symbol) {
        try {
            // 5-min bars, 60-day range — ~4 680 bars (free, no API key)
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=5m&range=60d"
                    .formatted(symbol);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "application/json");
            headers.set("Accept-Language", "en-US,en;q=0.9");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("Yahoo Finance chart endpoint returned {} for {}", response.getStatusCode(), symbol);
                return List.of();
            }

            return parseHistoricalResponse(response.getBody(), symbol);

        } catch (Exception e) {
            log.warn("Failed to fetch historical prices from Yahoo Finance for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — price endpoints
    // -------------------------------------------------------------------------

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
