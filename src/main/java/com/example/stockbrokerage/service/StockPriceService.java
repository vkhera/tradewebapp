package com.example.stockbrokerage.service;

import com.example.stockbrokerage.client.YahooFinanceClient;
import com.example.stockbrokerage.dto.DailyBar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service facade for stock price lookups.
 * All HTTP communication with Yahoo Finance is delegated to {@link YahooFinanceClient},
 * making this class fully testable without any network dependency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceService {

    private final YahooFinanceClient yahooFinanceClient;
    
    /**
     * Fetch current stock price from Yahoo Finance API.
     * The client implementation tries multiple endpoints in order to avoid rate limiting.
     */
    public BigDecimal getCurrentPrice(String symbol) {
        return yahooFinanceClient.getCurrentPrice(symbol);
    }
    
    /**
     * Get full quote data for a symbol.
     */
    public Map<String, Object> getQuote(String symbol) {
        return yahooFinanceClient.getQuote(symbol);
    }

    /**
     * Fetch daily OHLCV bars covering approximately the past {@code days} calendar days.
     */
    public List<DailyBar> getDailyBars(String symbol, int days) {
        return yahooFinanceClient.getDailyBars(symbol, days);
    }
}
