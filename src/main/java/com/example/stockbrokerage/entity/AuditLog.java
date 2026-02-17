package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_event_time", columnList = "event_time"),
    @Index(name = "idx_entity_type", columnList = "entity_type"),
    @Index(name = "idx_action", columnList = "action")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;
    
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // TRADE, CLIENT, RULE, etc.
    
    @Column(name = "entity_id")
    private Long entityId;
    
    @Column(nullable = false, length = 20)
    private String action; // CREATE, UPDATE, DELETE, EXECUTE
    
    @Column(name = "user_id", length = 50)
    private String userId;
    
    @Column(columnDefinition = "TEXT")
    private String details;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @PrePersist
    protected void onCreate() {
        eventTime = LocalDateTime.now();
    }
}
