package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
    
    List<AuditLog> findByEventTimeBetween(LocalDateTime start, LocalDateTime end);
    
    List<AuditLog> findByUserId(String userId);
    
    List<AuditLog> findByAction(String action);
}
