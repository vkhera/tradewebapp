package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily aggregate score of how well predictions performed, broken down by prediction type.
 *
 * <p>One row per (calendar day, prediction type), populated by the end-of-day
 * {@code PREDICTION_SCORING} job.  The four types are:
 * <ul>
 *   <li>{@link PredictionType#COMBINED}    – aggregate across all types</li>
 *   <li>{@link PredictionType#HOURLY_PRICE} – intraday hourly price predictions</li>
 *   <li>{@link PredictionType#SWING_TRADE}  – multi-day swing trade outcome predictions</li>
 *   <li>{@link PredictionType#TREND}        – technical-indicator directional analysis</li>
 * </ul>
 */
@Entity
@Table(name = "prediction_daily_score",
        indexes = { @Index(name = "idx_pds_score_date", columnList = "score_date") },
        uniqueConstraints = @UniqueConstraint(name = "uq_pds_score_date_type",
                columnNames = {"score_date", "prediction_type"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionDailyScore {

    public enum PredictionType {
        /** All prediction types aggregated together. */
        COMBINED,
        /** Intraday hourly price predictions (StockPricePrediction). SUCCESS = |pctError| ≤ 3 %. */
        HOURLY_PRICE,
        /** Multi-day swing trade outcome predictions (SwingTradePrediction). SUCCESS = outcome SUCCESS. */
        SWING_TRADE,
        /** Technical-indicator directional analysis (TrendPredictionResult). SUCCESS = direction matches. */
        TREND
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The calendar date this score covers. */
    @Column(name = "score_date", nullable = false)
    private LocalDate scoreDate;

    /** Which prediction type this row measures. */
    @Enumerated(EnumType.STRING)
    @Column(name = "prediction_type", nullable = false, length = 20)
    @Builder.Default
    private PredictionType predictionType = PredictionType.COMBINED;

    /** Total number of predictions that were resolved (actualPrice set) on this day. */
    @Column(name = "total_resolved", nullable = false)
    private int totalResolved;

    /** Predictions where |percentageError| ≤ 3 %. */
    @Column(name = "success_count", nullable = false)
    private int successCount;

    /** Predictions where |percentageError| > 3 %. */
    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    /** successCount / totalResolved × 100, or 0 when totalResolved = 0. */
    @Column(name = "success_rate_pct", nullable = false)
    private double successRatePct;

    /** Mean absolute price error across all resolved predictions. */
    @Column(name = "avg_absolute_error", precision = 12, scale = 4)
    private BigDecimal avgAbsoluteError;

    /** Mean percentage error (signed average) across all resolved predictions. */
    @Column(name = "avg_percentage_error", precision = 8, scale = 4)
    private BigDecimal avgPercentageError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
