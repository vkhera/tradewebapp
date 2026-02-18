package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.entity.AuditLog;
import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.repository.AuditLogRepository;
import com.example.stockbrokerage.repository.TradeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/trades")
@RequiredArgsConstructor
@Tag(name = "Trade Management (Admin)", description = "Admin APIs for viewing and managing all trades")
public class TradeAdminController {
    
    private final TradeRepository tradeRepository;
    private final AuditLogRepository auditLogRepository;
    
    @GetMapping
    @Operation(summary = "Get all trades", description = "Retrieve all trades in the system")
    public ResponseEntity<List<Trade>> getAllTrades() {
        List<Trade> trades = tradeRepository.findAll();
        return ResponseEntity.ok(trades);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get trade by ID", description = "Retrieve trade details")
    public ResponseEntity<Trade> getTradeById(@PathVariable Long id) {
        Trade trade = tradeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Trade not found"));
        return ResponseEntity.ok(trade);
    }
    
    @GetMapping("/{id}/audit-logs")
    @Operation(summary = "Get trade audit logs", description = "Retrieve all audit logs for a trade")
    public ResponseEntity<List<AuditLog>> getTradeAuditLogs(@PathVariable Long id) {
        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityId("TRADE", id);
        return ResponseEntity.ok(logs);
    }
    
    @GetMapping("/audit-logs")
    @Operation(summary = "Get all audit logs", description = "Retrieve all audit logs in the system")
    public ResponseEntity<List<AuditLog>> getAllAuditLogs() {
        List<AuditLog> logs = auditLogRepository.findAll();
        return ResponseEntity.ok(logs);
    }
}
