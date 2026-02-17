package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_client_id", columnList = "client_id"),
    @Index(name = "idx_symbol", columnList = "symbol"),
    @Index(name = "idx_trade_time", columnList = "trade_time")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "client_id", nullable = false)
    private Long clientId;
    
    @Column(nullable = false, length = 10)
    private String symbol;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private TradeType type; // BUY or SELL
    
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 10)
    private OrderType orderType; // MARKET or LIMIT
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeStatus status;
    
    @Column(name = "trade_time", nullable = false)
    private LocalDateTime tradeTime;
    
    @Column(name = "expiry_time")
    private LocalDateTime expiryTime;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "fraud_check_passed")
    private Boolean fraudCheckPassed;
    
    @Column(name = "fraud_check_reason", length = 500)
    private String fraudCheckReason;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        tradeTime = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum TradeType {
        BUY, SELL
    }
    
    public enum OrderType {
        MARKET, LIMIT
    }
    
    public enum TradeStatus {
        PENDING, VALIDATED, EXECUTED, REJECTED, CANCELLED, FAILED, EXPIRED
    }
}
