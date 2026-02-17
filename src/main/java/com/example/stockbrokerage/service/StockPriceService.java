package com.example.stockbrokerage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceService {
    
    private final RestTemplate restTemplate;
    
    public StockPriceService() {
        this.restTemplate = new RestTemplate();
        // Add User-Agent header to avoid being blocked as a bot
        this.restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            return execution.execute(request, body);
        });
    }
    
    /**
     * Fetch current stock price from Yahoo Finance API with fallback endpoints
     * Tries multiple endpoints in order to avoid rate limiting
     */
    public BigDecimal getCurrentPrice(String symbol) {
        // Try endpoint 1: v7/finance/quote (most permissive)
        BigDecimal price = tryQuoteEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            return price;
        }
        
        // Try endpoint 2: v8/finance/chart
        price = tryChartEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            return price;
        }
        
        // Try endpoint 3: v6/finance/quote (older, sometimes works)
        price = tryV6QuoteEndpoint(symbol);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            return price;
        }
        
        log.warn("All Yahoo Finance endpoints failed for symbol: {}", symbol);
        return BigDecimal.ZERO;
    }
    
    private BigDecimal tryQuoteEndpoint(String symbol) {
        try {
            String url = String.format(
                "https://query1.finance.yahoo.com/v7/finance/quote?symbols=%s",
                symbol
            );
            
            log.info("Trying v7/quote endpoint for symbol: {}", symbol);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("quoteResponse")) {
                Map<String, Object> quoteResponse = (Map<String, Object>) response.get("quoteResponse");
                
                if (quoteResponse.containsKey("result")) {
                    var results = (java.util.List<Map<String, Object>>) quoteResponse.get("result");
                    if (!results.isEmpty()) {
                        Map<String, Object> quote = results.get(0);
                        
                        // Try regularMarketPrice first, then fallback to other price fields
                        Object priceObj = quote.get("regularMarketPrice");
                        if (priceObj == null) {
                            priceObj = quote.get("ask");
                        }
                        if (priceObj == null) {
                            priceObj = quote.get("bid");
                        }
                        
                        if (priceObj != null) {
                            Double price = ((Number) priceObj).doubleValue();
                            log.info("✓ v7/quote succeeded for {}: {}", symbol, price);
                            return BigDecimal.valueOf(price);
                        }
                    }
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("✗ v7/quote rate limited for {}, trying next endpoint", symbol);
        } catch (Exception e) {
            log.warn("✗ v7/quote failed for {}: {}, trying next endpoint", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }
    
    private BigDecimal tryChartEndpoint(String symbol) {
        try {
            String url = String.format(
                "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d",
                symbol
            );
            
            log.info("Trying v8/chart endpoint for symbol: {}", symbol);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("chart")) {
                Map<String, Object> chart = (Map<String, Object>) response.get("chart");
                
                if (chart.containsKey("result")) {
                    var results = (java.util.List<Map<String, Object>>) chart.get("result");
                    if (!results.isEmpty()) {
                        Map<String, Object> result = results.get(0);
                        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
                        
                        if (meta != null && meta.containsKey("regularMarketPrice")) {
                            Double price = ((Number) meta.get("regularMarketPrice")).doubleValue();
                            log.info("✓ v8/chart succeeded for {}: {}", symbol, price);
                            return BigDecimal.valueOf(price);
                        }
                    }
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("✗ v8/chart rate limited for {}, trying next endpoint", symbol);
        } catch (Exception e) {
            log.warn("✗ v8/chart failed for {}: {}, trying next endpoint", symbol, e.getMessage());
        }
        return BigDecimal.ZERO;
    }
    
    private BigDecimal tryV6QuoteEndpoint(String symbol) {
        try {
            String url = String.format(
                "https://query2.finance.yahoo.com/v6/finance/quote?symbols=%s",
                symbol
            );
            
            log.info("Trying v6/quote endpoint for symbol: {}", symbol);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("quoteResponse")) {
                Map<String, Object> quoteResponse = (Map<String, Object>) response.get("quoteResponse");
                
                if (quoteResponse.containsKey("result")) {
                    var results = (java.util.List<Map<String, Object>>) quoteResponse.get("result");
                    if (!results.isEmpty()) {
                        Map<String, Object> quote = results.get(0);
                        
                        Object priceObj = quote.get("regularMarketPrice");
                        if (priceObj == null) {
                            priceObj = quote.get("ask");
                        }
                        if (priceObj == null) {
                            priceObj = quote.get("bid");
                        }
                        
                        if (priceObj != null) {
                            Double price = ((Number) priceObj).doubleValue();
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
    
    /**
     * Get stock quote with additional information
     */
    public Map<String, Object> getQuote(String symbol) {
        try {
            String url = String.format(
                "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d",
                symbol
            );
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response;
            
        } catch (Exception e) {
            log.error("Error fetching quote for symbol: {}", symbol, e);
            return Map.of("error", "Failed to fetch quote");
        }
    }
}
