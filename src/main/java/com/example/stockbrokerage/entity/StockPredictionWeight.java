package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mirrors the stock_predictions/{symbol}_pred_weights.csv files.
 * Stores the per-symbol, per-technique weights used by StockPricePredictionService.
 */
@Entity
@Table(name = "stock_prediction_weight", uniqueConstraints = {
    @UniqueConstraint(name = "uq_spw_symbol_technique", columnNames = {"symbol", "technique"})
}, indexes = {
    @Index(name = "idx_spw_symbol", columnList = "symbol")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockPredictionWeight {

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
    private LocalDateTime lastUpdated;
}
