package com.example.stockbrokerage.dto;

import java.math.BigDecimal;

/**
 * Snapshot quote for a market index (e.g. S&P 500, Dow, Nasdaq).
 *
 * @param symbol              Yahoo Finance ticker (e.g. "^GSPC", "GC=F")
 * @param name                Human-readable display name
 * @param price               Last regular-market price
 * @param change              Day change (price − previousClose)
 * @param changePct           Day change percent
 * @param postMarketPrice     After-hours price (null if not in post-market or unavailable)
 * @param postMarketChange    After-hours change vs. closing price (null if unavailable)
 * @param postMarketChangePct After-hours change percent (null if unavailable)
 */
public record MarketIndexQuote(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePct,
        BigDecimal postMarketPrice,
        BigDecimal postMarketChange,
        BigDecimal postMarketChangePct
) {}
