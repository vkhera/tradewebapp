package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {
    
    private final TradeRepository tradeRepository;
    private final ClientService clientService;
    
    public Map<String, Object> checkForFraud(Trade trade) {
        Map<String, Object> result = new HashMap<>();
        result.put("passed", true);
        StringBuilder reasons = new StringBuilder();
        
        try {
            Client client = clientService.getClientById(trade.getClientId());
            
            // Check 1: Client status
            if (client.getStatus() != Client.ClientStatus.ACTIVE) {
                result.put("passed", false);
                reasons.append("Client is not active. ");
            }
            
            // Check 2: Trading hours (9:30 AM - 4:00 PM)
            LocalTime tradeTime = trade.getTradeTime().toLocalTime();
            if (tradeTime.isBefore(LocalTime.of(9, 30)) || tradeTime.isAfter(LocalTime.of(16, 0))) {
                result.put("passed", false);
                reasons.append("Trade outside trading hours. ");
            }
            
            // Check 3: Daily trade limit
            LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);
            List<Trade> todayTrades = tradeRepository.findTodayTradesByClient(trade.getClientId(), startOfDay);
            
            BigDecimal todayTotal = todayTrades.stream()
                .map(t -> t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal tradeValue = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
            BigDecimal newTotal = todayTotal.add(tradeValue);
            
            if (client.getDailyTradeLimit() != null && newTotal.compareTo(client.getDailyTradeLimit()) > 0) {
                result.put("passed", false);
                reasons.append("Daily trade limit exceeded. ");
            }
            
            // Check 4: Unusual trade size
            if (trade.getQuantity() > 10000) {
                log.warn("Unusually large trade detected: {} shares of {}", trade.getQuantity(), trade.getSymbol());
                // Flag for manual review but don't reject
            }
            
            // Check 5: Account balance check for BUY orders
            if (trade.getType() == Trade.TradeType.BUY) {
                if (client.getAccountBalance().compareTo(tradeValue) < 0) {
                    result.put("passed", false);
                    reasons.append("Insufficient account balance. ");
                }
            }
            
            result.put("reason", reasons.toString());
            
        } catch (Exception e) {
            log.error("Error during fraud detection", e);
            result.put("passed", false);
            result.put("reason", "Fraud check error: " + e.getMessage());
        }
        
        return result;
    }
}
