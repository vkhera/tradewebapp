package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.Account;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.entity.Trade.TradeStatus;
import com.example.stockbrokerage.entity.Trade.TradeType;
import com.example.stockbrokerage.repository.AccountRepository;
import com.example.stockbrokerage.repository.ClientRepository;
import com.example.stockbrokerage.repository.PortfolioRepository;
import com.example.stockbrokerage.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {
    
    private final TradeRepository tradeRepository;
    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    
    /**
     * Runs every minute to reconcile portfolio and account balances
     */
    @Scheduled(fixedRate = 60000) // 1 minute in milliseconds
    @Transactional
    public void reconcileAccounts() {
        log.info("Starting account reconciliation");
        
        try {
            List<Client> allClients = clientRepository.findAll();
            
            for (Client client : allClients) {
                reconcileClientAccount(client);
            }
            
            log.info("Account reconciliation completed successfully");
        } catch (Exception e) {
            log.error("Error during account reconciliation", e);
        }
    }
    
    private void reconcileClientAccount(Client client) {
        log.debug("Reconciling account for client: {}", client.getId());
        
        // Get all executed trades for this client
        List<Trade> executedTrades = tradeRepository.findByClientIdAndStatus(client.getId(), TradeStatus.EXECUTED);
        
        // Rebuild portfolio from scratch based on executed trades
        Map<String, PortfolioPosition> positions = new HashMap<>();
        
        for (Trade trade : executedTrades) {
            String symbol = trade.getSymbol();
            PortfolioPosition position = positions.getOrDefault(symbol, new PortfolioPosition());
            
            if (trade.getType() == TradeType.BUY) {
                // Add to position
                BigDecimal totalCost = position.totalCost.add(
                    trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()))
                );
                int newQuantity = position.quantity + trade.getQuantity();
                
                position.quantity = newQuantity;
                position.totalCost = totalCost;
                if (newQuantity > 0) {
                    position.averagePrice = totalCost.divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);
                }
            } else {
                // Reduce from position
                position.quantity -= trade.getQuantity();
                if (position.quantity > 0) {
                    // Reduce total cost proportionally
                    position.totalCost = position.averagePrice.multiply(BigDecimal.valueOf(position.quantity));
                } else {
                    position.totalCost = BigDecimal.ZERO;
                    position.averagePrice = BigDecimal.ZERO;
                }
            }
            
            positions.put(symbol, position);
        }
        
        // Update portfolio entries
        // First, get existing portfolio entries
        List<Portfolio> existingPortfolios = portfolioRepository.findByClientId(client.getId());
        
        // Delete all existing entries
        portfolioRepository.deleteAll(existingPortfolios);
        
        // Create new entries based on calculated positions
        for (Map.Entry<String, PortfolioPosition> entry : positions.entrySet()) {
            if (entry.getValue().quantity > 0) {
                Portfolio portfolio = new Portfolio();
                portfolio.setClient(client);
                portfolio.setSymbol(entry.getKey());
                portfolio.setQuantity(entry.getValue().quantity);
                portfolio.setAveragePrice(entry.getValue().averagePrice);
                portfolioRepository.save(portfolio);
            }
        }
        
        // Reconcile account balance
        Account account = accountRepository.findByClientId(client.getId())
            .orElseThrow(() -> new RuntimeException("Account not found for client: " + client.getId()));
        
        // Calculate total spent on executed trades
        BigDecimal totalBuyAmount = executedTrades.stream()
            .filter(t -> t.getType() == TradeType.BUY)
            .map(t -> t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalSellAmount = executedTrades.stream()
            .filter(t -> t.getType() == TradeType.SELL)
            .map(t -> t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate reserved funds for pending limit orders
        List<Trade> pendingTrades = tradeRepository.findByClientIdAndStatus(client.getId(), TradeStatus.PENDING);
        
        BigDecimal reservedAmount = pendingTrades.stream()
            .filter(t -> t.getType() == TradeType.BUY) // Only BUY orders reserve funds
            .map(t -> t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Expected cash balance = initial balance + sells - buys
        // We need to get the client's initial balance from their account
        BigDecimal initialBalance = client.getAccountBalance(); // This is the opening balance
        BigDecimal expectedCashBalance = initialBalance.add(totalSellAmount).subtract(totalBuyAmount);
        
        // Update account if there's a discrepancy
        if (account.getCashBalance().compareTo(expectedCashBalance) != 0) {
            log.warn("Cash balance mismatch for client {}: expected {}, actual {}. Correcting...", 
                    client.getId(), expectedCashBalance, account.getCashBalance());
            account.setCashBalance(expectedCashBalance);
        }
        
        if (account.getReservedBalance().compareTo(reservedAmount) != 0) {
            log.warn("Reserved balance mismatch for client {}: expected {}, actual {}. Correcting...", 
                    client.getId(), reservedAmount, account.getReservedBalance());
            account.setReservedBalance(reservedAmount);
        }
        
        accountRepository.save(account);
        
        log.debug("Reconciliation complete for client {}: {} positions, cash={}, reserved={}", 
                client.getId(), positions.size(), expectedCashBalance, reservedAmount);
    }
    
    private static class PortfolioPosition {
        int quantity = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal averagePrice = BigDecimal.ZERO;
    }
}
