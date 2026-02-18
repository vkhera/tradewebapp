package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.TradeRequest;
import com.example.stockbrokerage.dto.TradeResponse;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.entity.Trade.TradeStatus;
import com.example.stockbrokerage.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeService {
    
    private final TradeRepository tradeRepository;
    private final ClientService clientService;
    private final FraudDetectionService fraudDetectionService;
    private final RuleEngineService ruleEngineService;
    private final AuditService auditService;
    private final PortfolioService portfolioService;
    private final AccountService accountService;
    
    @Transactional
    public TradeResponse executeTrade(TradeRequest request) {
        long startTime = System.nanoTime();
        
        // Validate client exists
        Client client = clientService.getClientById(request.getClientId());
        
        // Set default order type if not provided
        Trade.OrderType orderType = request.getOrderType() != null ? request.getOrderType() : Trade.OrderType.MARKET;
        
        // Create trade entity
        Trade trade = Trade.builder()
            .clientId(request.getClientId())
            .symbol(request.getSymbol())
            .quantity(request.getQuantity())
            .price(request.getPrice())
            .type(request.getType())
            .orderType(orderType)
            .status(TradeStatus.PENDING)
            .tradeTime(java.time.LocalDateTime.now())
            .build();
        
        // Set expiry time to end of day for day orders
        if (orderType == Trade.OrderType.LIMIT) {
            trade.setExpiryTime(java.time.LocalDateTime.now().toLocalDate().atTime(23, 59, 59));
        }
        
        // Run fraud detection
        Map<String, Object> fraudResult = fraudDetectionService.checkForFraud(trade);
        trade.setFraudCheckPassed((Boolean) fraudResult.get("passed"));
        trade.setFraudCheckReason((String) fraudResult.get("reason"));
        
        if (!trade.getFraudCheckPassed()) {
            trade.setStatus(TradeStatus.REJECTED);
            Trade saved = tradeRepository.save(trade);
            auditService.logTradeEvent(saved.getId(), "REJECT", "SYSTEM", "Failed fraud check: " + trade.getFraudCheckReason());
            
            long duration = System.nanoTime() - startTime;
            log.info("Trade rejected in {} ns", duration);
            
            return mapToResponse(saved);
        }
        
        // Apply rule engine
        Map<String, Object> ruleResult = ruleEngineService.evaluateTrade(trade, request.getClientId());
        
        if (!(Boolean) ruleResult.get("approved")) {
            trade.setStatus(TradeStatus.REJECTED);
            trade.setFraudCheckReason((String) ruleResult.get("reasons"));
            Trade saved = tradeRepository.save(trade);
            auditService.logTradeEvent(saved.getId(), "REJECT", "SYSTEM", "Failed rule validation");
            
            long duration = System.nanoTime() - startTime;
            log.info("Trade rejected by rules in {} ns", duration);
            
            return mapToResponse(saved);
        }
        
        // Execute trade based on order type
        // For MARKET orders, execute immediately
        // For LIMIT orders, keep as PENDING until price condition is met
        BigDecimal tradeAmount = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
        
        if (orderType == Trade.OrderType.MARKET) {
            trade.setStatus(TradeStatus.EXECUTED);
            
            // Update portfolio and account based on trade type
            if (trade.getType() == Trade.TradeType.BUY) {
                portfolioService.updatePortfolio(client, trade.getSymbol(), trade.getQuantity(), trade.getPrice());
                accountService.deductFunds(client.getId(), tradeAmount);
            } else {
                portfolioService.updatePortfolio(client, trade.getSymbol(), -trade.getQuantity(), trade.getPrice());
                accountService.addFunds(client.getId(), tradeAmount);
            }
            
            auditService.logTradeEvent(null, "EXECUTE", "SYSTEM", "Market order executed successfully");
        } else {
            // Limit orders remain PENDING - reserve funds for BUY orders
            trade.setStatus(TradeStatus.PENDING);
            
            if (trade.getType() == Trade.TradeType.BUY) {
                accountService.reserveFunds(client.getId(), tradeAmount);
            }
            
            auditService.logTradeEvent(null, "CREATE", "SYSTEM", "Limit order created, awaiting execution");
        }
        
        Trade saved = tradeRepository.save(trade);
        
        long duration = System.nanoTime() - startTime;
        log.info("Trade {} in {} ns ({}ms)", trade.getStatus(), duration, duration / 1_000_000);
        
        return mapToResponse(saved);
    }
    
    public TradeResponse getTradeById(Long id) {
        Trade trade = tradeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Trade not found with id: " + id));
        return mapToResponse(trade);
    }
    
    public List<TradeResponse> getAllTrades() {
        return tradeRepository.findAll().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    public List<TradeResponse> getTradesByClient(Long clientId) {
        return tradeRepository.findByClientId(clientId).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    public List<TradeResponse> getTradesBySymbol(String symbol) {
        return tradeRepository.findBySymbol(symbol).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public void cancelTrade(Long id) {
        Trade trade = tradeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Trade not found with id: " + id));
        
        if (trade.getStatus() == TradeStatus.EXECUTED) {
            throw new RuntimeException("Cannot cancel executed trade");
        }
        
        trade.setStatus(TradeStatus.CANCELLED);
        tradeRepository.save(trade);
        auditService.logTradeEvent(id, "CANCEL", "SYSTEM", "Trade cancelled");
    }
    
    private TradeResponse mapToResponse(Trade trade) {
        return TradeResponse.builder()
            .id(trade.getId())
            .clientId(trade.getClientId())
            .symbol(trade.getSymbol())
            .quantity(trade.getQuantity())
            .price(trade.getPrice())
            .type(trade.getType())
            .orderType(trade.getOrderType())
            .status(trade.getStatus())
            .tradeTime(trade.getTradeTime())
            .expiryTime(trade.getExpiryTime())
            .fraudCheckPassed(trade.getFraudCheckPassed())
            .fraudCheckReason(trade.getFraudCheckReason())
            .build();
    }
}
