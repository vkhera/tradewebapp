package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted record of a suggested trade generated for a client holding.
 * Status is updated by a daily scheduler: SUCCESS when the buy-back target price is hit,
 * FAILED when the target is not reached within 7 days of the suggestion date.
 */
@Entity
@Table(name = "suggested_trade_records",
       indexes = {
           @Index(name = "idx_str_client_date",  columnList = "client_id, suggested_date"),
           @Index(name = "idx_str_status_date",  columnList = "status, suggested_date")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedTradeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "suggested_date", nullable = false)
    private LocalDateTime suggestedDate;

    /** Action at the time of suggestion: SELL or WATCH. */
    @Column(nullable = false, length = 10)
    private String action;

    /** Price at which the stock was trading when the suggestion was generated. */
    @Column(name = "current_price_at_suggestion", nullable = false, precision = 15, scale = 4)
    private BigDecimal currentPriceAtSuggestion;

    /** ATR(14) at the time of the suggestion. */
    @Column(name = "atr14", precision = 15, scale = 4)
    private BigDecimal atr14;

    /** Average predicted price across all prediction models at time of suggestion. */
    @Column(name = "avg_predicted_price", precision = 15, scale = 4)
    private BigDecimal avgPredictedPrice;

    /** Expected change % at time of suggestion (negative = predicted drop). */
    @Column(name = "expected_change_pct", precision = 8, scale = 2)
    private BigDecimal expectedChangePct;

    /** Suggested sell price (current price at time of suggestion for SELL signals). */
    @Column(name = "suggested_sell_price", precision = 15, scale = 4)
    private BigDecimal suggestedSellPrice;

    /**
     * Target price to re-enter the position (currentPrice - ATR14).
     * The scheduler marks a suggestion SUCCESS when the actual price drops to or below this target.
     */
    @Column(name = "suggested_buy_back_price", precision = 15, scale = 4)
    private BigDecimal suggestedBuyBackPrice;

    @Column(nullable = false)
    private Integer confidence;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    /**
     * PENDING  – awaiting outcome check.<br>
     * SUCCESS  – buy-back target price was hit within 7 days.<br>
     * FAILED   – target not reached within 7 days.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeOutcomeStatus status;

    /** When the status was last changed from PENDING to SUCCESS or FAILED. */
    @Column(name = "resolved_date")
    private LocalDateTime resolvedDate;

    public enum TradeOutcomeStatus {
        PENDING, SUCCESS, FAILED
    }
}
