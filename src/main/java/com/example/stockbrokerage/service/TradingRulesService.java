package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.Account;
import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.repository.AccountRepository;
import com.example.stockbrokerage.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingRulesService {
    
    private final AccountRepository accountRepository;
    private final TradeRepository tradeRepository;
    
    /**
     * Validate if client has sufficient funds for a BUY trade
     * Takes into account: cash balance, reserved balance for pending orders
     */
    public boolean validateSufficientFunds(Long clientId, BigDecimal tradeAmount) {
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found for client: " + clientId));
        
        // Calculate total pending order amount
        BigDecimal pendingOrdersAmount = calculatePendingOrdersAmount(clientId);
        
        // Available balance = cash balance - reserved balance (which includes pending orders)
        BigDecimal availableBalance = account.getAvailableBalance();
        
        log.info("Client {}: Cash={}, Reserved={}, Available={}, Required={}", 
            clientId, account.getCashBalance(), account.getReservedBalance(), 
            availableBalance, tradeAmount);
        
        return availableBalance.compareTo(tradeAmount) >= 0;
    }
    
    /**
     * Calculate total amount reserved for pending BUY orders
     */
    private BigDecimal calculatePendingOrdersAmount(Long clientId) {
        List<Trade> pendingTrades = tradeRepository.findByClientIdAndStatus(
            clientId, 
            Trade.TradeStatus.PENDING
        );
        
        return pendingTrades.stream()
            .filter(trade -> trade.getType() == Trade.TradeType.BUY)
            .map(trade -> trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Reserve funds for a pending BUY order
     */
    public void reserveFundsForTrade(Long clientId, BigDecimal amount) {
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        account.setReservedBalance(account.getReservedBalance().add(amount));
        accountRepository.save(account);
    }
    
    /**
     * Execute trade and update account balance
     */
    public void executeTrade(Long clientId, Trade trade) {
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        BigDecimal tradeAmount = trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()));
        
        if (trade.getType() == Trade.TradeType.BUY) {
            // Deduct from both cash and reserved balance
            account.setCashBalance(account.getCashBalance().subtract(tradeAmount));
            account.setReservedBalance(account.getReservedBalance().subtract(tradeAmount));
        } else {
            // Add to cash balance
            account.setCashBalance(account.getCashBalance().add(tradeAmount));
        }
        
        accountRepository.save(account);
    }
}
