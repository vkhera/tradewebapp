package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Mirrors the trend_predictions/{symbol}_weights.csv files.
 * Stores per-symbol technique weights used by TrendAnalysisService.
 */
@Entity
@Table(name = "trend_prediction_weight", uniqueConstraints = {
    @UniqueConstraint(name = "uq_tpw_symbol_technique", columnNames = {"symbol", "technique"})
}, indexes = {
    @Index(name = "idx_tpw_symbol", columnList = "symbol")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPredictionWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 50)
    private String technique;

    @Column(nullable = false)
    private Double weight;

    @Column(name = "last_updated", nullable = false)
    private LocalDate lastUpdated;
}
