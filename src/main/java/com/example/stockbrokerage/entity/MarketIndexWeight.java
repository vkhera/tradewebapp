package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Stores the learned per-stock, per-index weights used by {@link
 * com.example.stockbrokerage.service.MarketIndexService} when computing how much
 * each macro-market index (IWM, QQQ, VOO, DIA, VXVY) influences an individual
 * stock's price prediction and trend direction.
 *
 * <p>One row exists per (stock symbol, index symbol) pair.  The {@code weight}
 * starts as the magnitude of the rolling Pearson correlation and is updated over
 * time based on how accurately the index predicted actual price movements.
 *
 * <p>The {@code correlation} column stores the most recently computed rolling
 * Pearson correlation coefficient (−1 to +1) between the stock's 5-min returns
 * and the index's 5-min returns.  A negative value means the index and stock
 * move inversely (typical for VXVY vs equity holdings).
 */
@Entity
@Table(name = "market_index_weight", uniqueConstraints = {
    @UniqueConstraint(name = "uq_miw_symbol_index", columnNames = {"symbol", "index_symbol"})
}, indexes = {
    @Index(name = "idx_miw_symbol", columnList = "symbol")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketIndexWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Portfolio stock symbol (e.g. NVDA, IWM, TNA). */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** Market index symbol: IWM, QQQ, VOO, DIA, or VXVY. */
    @Column(name = "index_symbol", nullable = false, length = 20)
    private String indexSymbol;

    /**
     * Current learned weight for this index's contribution to the stock's prediction.
     * Across all five indices for a given stock the weights sum to 1.0.
     */
    @Column(nullable = false)
    private Double weight;

    /**
     * Rolling Pearson correlation between the stock's 5-min log-returns and
     * the index's 5-min log-returns over the last 24 hours (288 bars).
     * Stored as an informational metric; the learning uses directional accuracy.
     */
    @Column(nullable = false)
    private Double correlation;

    @Column(name = "last_updated", nullable = false)
    private LocalDate lastUpdated;
}
