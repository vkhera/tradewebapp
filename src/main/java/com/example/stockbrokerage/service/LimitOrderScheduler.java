package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.entity.Trade.TradeStatus;
import com.example.stockbrokerage.entity.Trade.TradeType;
import com.example.stockbrokerage.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LimitOrderScheduler {
    
    private final TradeRepository tradeRepository;
    private final StockPriceService stockPriceService;
    private final AuditService auditService;
    private final PortfolioService portfolioService;
    private final AccountService accountService;
    private final ClientService clientService;
    
    /**
     * Runs every 5 minutes to check and execute limit orders
     */
    @Scheduled(fixedRate = 300000) // 5 minutes in milliseconds
    @Transactional
    public void processLimitOrders() {
        log.info("Starting limit order processing batch");
        
        LocalDateTime now = LocalDateTime.now();
        List<Trade> pendingLimitOrders = tradeRepository.findActiveLimitOrders(now);
        
        if (pendingLimitOrders.isEmpty()) {
            log.info("No pending limit orders to process");
            return;
        }
        
        log.info("Found {} pending limit orders to evaluate", pendingLimitOrders.size());
        
        int executed = 0;
        int expired = 0;
        
        for (Trade trade : pendingLimitOrders) {
            try {
                // Check if order has expired
                if (trade.getExpiryTime() != null && trade.getExpiryTime().isBefore(now)) {
                    trade.setStatus(TradeStatus.EXPIRED);
                    tradeRepository.save(trade);
                    auditService.logTradeEvent(trade.getId(), "EXPIRE", "SYSTEM", "Limit order expired");
                    expired++;
                    log.info("Trade {} expired", trade.getId());
                    continue;
                }
                
                // Get current market price
                BigDecimal currentPrice = stockPriceService.getCurrentPrice(trade.getSymbol());
                
                if (currentPrice.compareTo(BigDecimal.ZERO) == 0) {
                    log.warn("Unable to get price for symbol {}, skipping trade {}", trade.getSymbol(), trade.getId());
                    continue;
                }
                
                boolean shouldExecute = false;
                
                // Check if price conditions are met
                if (trade.getType() == TradeType.BUY) {
                    // For BUY orders: execute if current price <= limit price
                    if (currentPrice.compareTo(trade.getPrice()) <= 0) {
                        shouldExecute = true;
                        log.info("BUY limit order {} can be executed: current price {} <= limit price {}", 
                                trade.getId(), currentPrice, trade.getPrice());
                    }
                } else if (trade.getType() == TradeType.SELL) {
                    // For SELL orders: execute if current price >= limit price
                    if (currentPrice.compareTo(trade.getPrice()) >= 0) {
                        shouldExecute = true;
                        log.info("SELL limit order {} can be executed: current price {} >= limit price {}", 
                                trade.getId(), currentPrice, trade.getPrice());
                    }
                }
                
                if (shouldExecute) {
                    trade.setStatus(TradeStatus.EXECUTED);
                    trade.setTradeTime(now); // Update to actual execution time
                    
                    // Update portfolio and account
                    var client = clientService.getClientById(trade.getClientId());
                    BigDecimal tradeAmount = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
                    
                    if (trade.getType() == TradeType.BUY) {
                        // For BUY: update portfolio, deduct funds (release reserved + deduct from cash)
                        portfolioService.updatePortfolio(client, trade.getSymbol(), trade.getQuantity(), trade.getPrice());
                        accountService.deductFunds(trade.getClientId(), tradeAmount);
                    } else {
                        // For SELL: update portfolio (reduce quantity), add funds to cash
                        portfolioService.updatePortfolio(client, trade.getSymbol(), -trade.getQuantity(), trade.getPrice());
                        accountService.addFunds(trade.getClientId(), tradeAmount);
                    }
                    
                    tradeRepository.save(trade);
                    auditService.logTradeEvent(trade.getId(), "EXECUTE", "SYSTEM",
                        "Limit order executed at market price %s".formatted(currentPrice));
                    executed++;
                    log.info("Successfully executed limit order {}", trade.getId());
                }
                
            } catch (Exception e) {
                log.error("Error processing limit order {}: {}", trade.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Limit order batch complete: {} executed, {} expired, {} still pending", 
                executed, expired, (pendingLimitOrders.size() - executed - expired));
    }
}
