package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit trail of trend prediction weight changes.
 * A row is inserted every time a weight is updated, preserving full history.
 */
@Entity
@Table(name = "trend_prediction_weight_history", indexes = {
    @Index(name = "idx_tpwh_symbol", columnList = "symbol"),
    @Index(name = "idx_tpwh_changed_at", columnList = "changed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPredictionWeightHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 50)
    private String technique;

    @Column(name = "previous_weight")
    private Double previousWeight;

    @Column(name = "new_weight", nullable = false)
    private Double newWeight;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
