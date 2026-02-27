package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mirrors the stock_predictions/{symbol}_prices.csv files.
 * Each row is one 5-minute bar for a given symbol, synced by the nightly data-sync batch job.
 */
@Entity
@Table(name = "stock_price_cache", uniqueConstraints = {
    @UniqueConstraint(name = "uq_spc_symbol_bar_time", columnNames = {"symbol", "bar_time"})
}, indexes = {
    @Index(name = "idx_spc_symbol_bar_time", columnList = "symbol, bar_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    /** Approximate 5-minute bar timestamp (generated from fetch time). */
    @Column(name = "bar_time", nullable = false)
    private LocalDateTime barTime;

    @Column(name = "close_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal closePrice;

    /** When this batch of prices was fetched from Yahoo Finance. */
    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) syncedAt = LocalDateTime.now();
    }
}
