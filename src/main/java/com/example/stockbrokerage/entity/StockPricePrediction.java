package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_price_prediction", indexes = {
    @Index(name = "idx_spp_symbol_target", columnList = "symbol, target_hour"),
    @Index(name = "idx_spp_symbol_created", columnList = "symbol, created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockPricePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 50)
    private String technique;

    @Column(name = "prediction_made_at", nullable = false)
    private LocalDateTime predictionMadeAt;

    @Column(name = "target_hour", nullable = false)
    private LocalDateTime targetHour;

    @Column(name = "predicted_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal predictedPrice;

    @Column(name = "actual_price", precision = 12, scale = 4)
    private BigDecimal actualPrice;

    @Column(name = "absolute_error", precision = 12, scale = 4)
    private BigDecimal absoluteError;

    @Column(name = "percentage_error", precision = 8, scale = 4)
    private BigDecimal percentageError;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
