package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.AuditLog;
import com.example.stockbrokerage.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    
    private final AuditLogRepository auditLogRepository;
    
    @Async
    @Transactional
    public void logEvent(String entityType, Long entityId, String action, String userId, String details, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .userId(userId)
                .details(details)
                .ipAddress(ipAddress)
                .eventTime(LocalDateTime.now())
                .build();
            
            auditLogRepository.save(auditLog);
            log.debug("Audit log created for {} {} on {}", action, entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to create audit log", e);
        }
    }
    
    public void logTradeEvent(Long tradeId, String action, String userId, String details) {
        logEvent("TRADE", tradeId, action, userId, details, null);
    }
    
    public void logClientEvent(Long clientId, String action, String userId, String details) {
        logEvent("CLIENT", clientId, action, userId, details, null);
    }
    
    public void logRuleEvent(Long ruleId, String action, String userId, String details) {
        logEvent("RULE", ruleId, action, userId, details, null);
    }
}
