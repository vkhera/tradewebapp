package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.PortfolioResponse;
import com.example.stockbrokerage.dto.PortfolioSummaryResponse;
import com.example.stockbrokerage.entity.Account;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.repository.AccountRepository;
import com.example.stockbrokerage.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioService {
    
    private final PortfolioRepository portfolioRepository;
    private final StockPriceService stockPriceService;
    private final AccountRepository accountRepository;
    
    public List<PortfolioResponse> getClientPortfolio(Long clientId) {
        List<Portfolio> portfolios = portfolioRepository.findByClientId(clientId);
        
        return portfolios.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    public PortfolioSummaryResponse getClientPortfolioSummary(Long clientId) {
        // Get portfolio holdings
        List<PortfolioResponse> holdings = getClientPortfolio(clientId);
        
        // Get account information
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found for client: " + clientId));
        
        // Calculate totals
        BigDecimal totalPortfolioValue = holdings.stream()
            .map(PortfolioResponse::getTotalValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalInvestedValue = holdings.stream()
            .map(h -> h.getAveragePrice().multiply(BigDecimal.valueOf(h.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalProfitLoss = totalPortfolioValue.subtract(totalInvestedValue);
        
        BigDecimal totalProfitLossPercent = totalInvestedValue.compareTo(BigDecimal.ZERO) > 0
            ? totalProfitLoss.divide(totalInvestedValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;
        
        return new PortfolioSummaryResponse(
            account.getCashBalance(),
            account.getReservedBalance(),
            account.getAvailableBalance(),
            holdings,
            totalPortfolioValue,
            totalInvestedValue,
            totalProfitLoss,
            totalProfitLossPercent
        );
    }
    
    private PortfolioResponse convertToResponse(Portfolio portfolio) {
        BigDecimal currentPrice = stockPriceService.getCurrentPrice(portfolio.getSymbol());
        BigDecimal totalValue = currentPrice.multiply(BigDecimal.valueOf(portfolio.getQuantity()));
        BigDecimal investedValue = portfolio.getAveragePrice().multiply(BigDecimal.valueOf(portfolio.getQuantity()));
        BigDecimal profitLoss = totalValue.subtract(investedValue);
        BigDecimal profitLossPercent = investedValue.compareTo(BigDecimal.ZERO) > 0
            ? profitLoss.divide(investedValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;
        
        return new PortfolioResponse(
            portfolio.getId(),
            portfolio.getSymbol(),
            portfolio.getQuantity(),
            portfolio.getAveragePrice(),
            currentPrice,
            totalValue,
            profitLoss,
            profitLossPercent
        );
    }
    
    public void updatePortfolio(Client client, String symbol, Integer quantity, BigDecimal price) {
        var portfolioOpt = portfolioRepository.findByClientAndSymbol(client, symbol);
        
        if (portfolioOpt.isPresent()) {
            Portfolio portfolio = portfolioOpt.get();
            int newQuantity = portfolio.getQuantity() + quantity;
            
            if (newQuantity <= 0) {
                portfolioRepository.delete(portfolio);
            } else {
                BigDecimal totalCost = portfolio.getAveragePrice()
                    .multiply(BigDecimal.valueOf(portfolio.getQuantity()))
                    .add(price.multiply(BigDecimal.valueOf(quantity)));
                BigDecimal newAvgPrice = totalCost.divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);
                
                portfolio.setQuantity(newQuantity);
                portfolio.setAveragePrice(newAvgPrice);
                portfolioRepository.save(portfolio);
            }
        } else if (quantity > 0) {
            Portfolio portfolio = new Portfolio();
            portfolio.setClient(client);
            portfolio.setSymbol(symbol);
            portfolio.setQuantity(quantity);
            portfolio.setAveragePrice(price);
            portfolioRepository.save(portfolio);
        }
    }
}
