package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for a swing-trade suggestion returned by the API.
 * Also used as the history record shape (status is PENDING for active, SUCCESS/FAILED for resolved).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwingTradeSuggestionResponse {

    private Long id;

    /** Ticker symbol (e.g. NVDA). */
    private String symbol;

    /** Number of shares currently held by the client. */
    private Integer quantity;

    /** Current market price at the time of suggestion. */
    private BigDecimal currentPrice;

    /**
     * Recommended action:
     * HOLD – bullish; hold (or add to) position and expect price to rise to targetPrice.
     * SELL – bearish; sell now and re-enter around targetPrice.
     */
    private String action;

    /**
     * Target price: upper bound to exit HOLD position, or re-entry price for SELL.
     */
    private BigDecimal targetPrice;

    /** Stop-loss price: 3% below entry for HOLD, 2% above re-entry for SELL. */
    private BigDecimal stopLoss;

    /**
     * Expected percentage return: positive for both HOLD (upside) and SELL (avoided decline).
     *
     * <p>Used as the primary sort key – top 5 by highest potential return are returned.
     */
    private BigDecimal predictedReturnPct;

    /** Estimated number of trading days to reach the target price. */
    private Integer holdDaysEstimated;

    /** Confidence score 0–100 based on weighted strategy agreement. */
    private Integer confidence;

    /**
     * Comma-separated names of strategies that contributed the strongest signals
     * (e.g. "RSI, MACD, BOLLINGER").
     */
    private String topStrategies;

    /** Human-readable explanation of the combined signal. */
    private String reasoning;

    /** Eastern-Time timestamp when this suggestion was generated. */
    private LocalDateTime suggestedDate;

    /** PENDING, SUCCESS, or FAILED. */
    private String status;

    /** Timestamp when the outcome was resolved (null while PENDING). */
    private LocalDateTime resolvedDate;

    /** Actual exit price recorded at resolution. */
    private BigDecimal actualExitPrice;

    /** Actual return percentage at resolution. */
    private BigDecimal actualReturnPct;
}
