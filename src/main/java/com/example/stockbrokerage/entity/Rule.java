package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 100)
    private String ruleName;
    
    @Column(nullable = false, length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleType ruleType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleLevel level; // APPLICATION, CLIENT, TRADE
    
    @Column(name = "client_id")
    private Long clientId; // Only for CLIENT level rules
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ruleContent; // DRL content or JSON configuration
    
    @Column(nullable = false)
    private Boolean active;
    
    @Column(nullable = false)
    private Integer priority;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by", length = 50)
    private String createdBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum RuleType {
        FRAUD_CHECK, RISK_LIMIT, TRADING_HOURS, POSITION_LIMIT, PRICE_VALIDATION
    }
    
    public enum RuleLevel {
        APPLICATION, CLIENT, TRADE
    }
}
