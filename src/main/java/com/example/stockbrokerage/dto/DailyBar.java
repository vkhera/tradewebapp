package com.example.stockbrokerage.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single daily OHLCV bar returned from Yahoo Finance.
 */
public record DailyBar(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
