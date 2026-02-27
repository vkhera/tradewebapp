package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents a single suggested trade (sell + buy-back) for the portfolio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedTradeResponse {

    /** Stock ticker symbol (e.g. NVDA). */
    private String symbol;

    /** Number of shares currently held. */
    private Integer quantity;

    /** Current market price per share. */
    private BigDecimal currentPrice;

    /** Wilder's ATR(14) – daily expected price range. */
    private BigDecimal atr14;

    /** Average predicted closing price across the next 8 hours. */
    private BigDecimal avgPredictedPrice;

    /**
     * Expected price change as a percentage.
     * Negative ⇒ downside; positive ⇒ upside.
     */
    private BigDecimal expectedChangePct;

    /** Recommended action: SELL or WATCH. */
    private String action;

    /**
     * Suggested sell price (near current price when action == SELL).
     * Null when action == WATCH.
     */
    private BigDecimal suggestedSellPrice;

    /**
     * Suggested buy-back price (current price minus one ATR when action == SELL).
     * Null when action == WATCH.
     */
    private BigDecimal suggestedBuyBackPrice;

    /**
     * Confidence score 0-100 based on the magnitude of the expected downside
     * and number of prediction techniques that agree.
     */
    private Integer confidence;

    /** Human-readable explanation of the suggestion. */
    private String reasoning;
}
