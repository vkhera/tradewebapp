package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mirrors the trend_predictions/{symbol}_predictions.csv files.
 * One row per symbol per date; updated/replaced on each batch run.
 */
@Entity
@Table(name = "trend_prediction_result", uniqueConstraints = {
    @UniqueConstraint(name = "uq_tpr_symbol_date", columnNames = {"symbol", "prediction_date"})
}, indexes = {
    @Index(name = "idx_tpr_symbol_date", columnList = "symbol, prediction_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPredictionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "prediction_date", nullable = false)
    private LocalDate predictionDate;

    @Column(name = "overall_trend", nullable = false, length = 20)
    private String overallTrend;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "ma_crossover", length = 20)
    private String maCrossover;

    @Column(length = 20)
    private String rsi;

    @Column(length = 20)
    private String macd;

    @Column(name = "price_momentum", length = 20)
    private String priceMomentum;

    @Column(name = "volume_trend", length = 20)
    private String volumeTrend;

    @Column(name = "index_momentum", length = 20)
    private String indexMomentum;

    @Column(name = "options_sentiment", length = 20)
    private String optionsSentiment;

    @Column(name = "news_sentiment", length = 20)
    private String newsSentiment;

    @Column(name = "etf_signal", length = 20)
    private String etfSignal;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
