package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private Client client;
    
    @Column(name = "cash_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashBalance;
    
    @Column(name = "reserved_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal reservedBalance = BigDecimal.ZERO;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getAvailableBalance() {
        return cashBalance.subtract(reservedBalance);
    }
}
