package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Adaptive weight for each swing-trade strategy.
 *
 * <p>Initial weight for each strategy is 0.20 (equal weighting across 5 strategies).
 * Weights are updated daily after resolving swing trade outcomes:
 * <ul>
 *   <li>WIN  (strategy signal aligned with SUCCESS outcome): weight × 1.15, capped at 0.60.</li>
 *   <li>LOSS (strategy signal aligned with FAILED outcome):  weight × 0.85, floored at 0.05.</li>
 * </ul>
 * Weights are renormalised to sum to 1.0 after each update cycle.
 */
@Entity
@Table(name = "swing_strategy_weights")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwingStrategyWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Strategy identifier: RSI, MACD, BOLLINGER, EMA_CROSS, MOMENTUM. */
    @Column(name = "strategy_name", nullable = false, unique = true, length = 50)
    private String strategyName;

    /** Current adaptive weight in range [0.05, 0.60]. All weights sum to 1.0. */
    @Column(nullable = false)
    private double weight;

    /** Cumulative count of successful outcomes credited to this strategy. */
    @Column(name = "win_count", nullable = false)
    private int winCount;

    /** Cumulative count of failed outcomes credited to this strategy. */
    @Column(name = "loss_count", nullable = false)
    private int lossCount;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
