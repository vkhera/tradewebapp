package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted record of a multi-day swing trade suggestion generated for a client's holding.
 *
 * <p>Suggestions are evaluated daily:
 * <ul>
 *   <li>SUCCESS – price reached {@code targetPrice} within {@code holdDaysEstimated}.</li>
 *   <li>FAILED  – {@code holdDaysEstimated} days passed without hitting the target.</li>
 * </ul>
 *
 * <p>All timestamps are stored in Eastern Time (America/New_York).
 */
@Entity
@Table(name = "swing_trade_predictions",
       indexes = {
           @Index(name = "idx_stp_client_date",  columnList = "client_id, suggested_date"),
           @Index(name = "idx_stp_status_date",  columnList = "status, suggested_date")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwingTradePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * Recommended action for the held stock:
     * <ul>
     *   <li>HOLD – bullish swing; hold (or add) and expect price to reach {@code targetPrice}.</li>
     *   <li>SELL – bearish swing; sell now and re-enter around {@code targetPrice}.</li>
     * </ul>
     */
    @Column(nullable = false, length = 10)
    private String action;

    /** Current market price at time of suggestion (entry price for HOLD; exit price for SELL). */
    @Column(name = "entry_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal entryPrice;

    /** Expected price target: upper price for HOLD, re-entry price for SELL. */
    @Column(name = "target_price", precision = 15, scale = 4)
    private BigDecimal targetPrice;

    /** Stop-loss level. For HOLD: 3% below entry. For SELL: 2% above re-entry target. */
    @Column(name = "stop_loss", precision = 15, scale = 4)
    private BigDecimal stopLoss;

    /**
     * Expected percentage return from this swing trade action.
     * Always positive: upside % for HOLD, capital-preservation % (avoided decline) for SELL.
     */
    @Column(name = "predicted_return_pct", precision = 8, scale = 2)
    private BigDecimal predictedReturnPct;

    /** Estimated number of trading days to hold before target is reached. */
    @Column(name = "hold_days_estimated")
    private Integer holdDaysEstimated;

    /** Confidence score 0–100 derived from weighted strategy agreement. */
    @Column(nullable = false)
    private Integer confidence;

    /** Comma-separated list of strategies that generated a strong signal (e.g. "RSI,MACD,BOLLINGER"). */
    @Column(name = "top_strategies", length = 200)
    private String topStrategies;

    /** Human-readable explanation of why this suggestion was generated. */
    @Column(columnDefinition = "TEXT")
    private String reasoning;

    /** JSON array of per-strategy signals stored for weight-update processing. */
    @Column(name = "strategy_signals", columnDefinition = "TEXT")
    private String strategySignals;

    /** Timestamp when the suggestion was created (Eastern Time). */
    @Column(name = "suggested_date", nullable = false)
    private LocalDateTime suggestedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SwingOutcomeStatus status;

    /** Timestamp when the outcome was determined (null while PENDING). */
    @Column(name = "resolved_date")
    private LocalDateTime resolvedDate;

    /** Actual price at which the position was (or would have been) closed. */
    @Column(name = "actual_exit_price", precision = 15, scale = 4)
    private BigDecimal actualExitPrice;

    /** Realised return percentage at resolution. */
    @Column(name = "actual_return_pct", precision = 8, scale = 2)
    private BigDecimal actualReturnPct;

    public enum SwingOutcomeStatus {
        PENDING, SUCCESS, FAILED
    }
}
