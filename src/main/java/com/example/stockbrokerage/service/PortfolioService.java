package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.PortfolioResponse;
import com.example.stockbrokerage.dto.PortfolioSummaryResponse;
import com.example.stockbrokerage.entity.Account;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.repository.AccountRepository;
import com.example.stockbrokerage.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {
    
    private final PortfolioRepository portfolioRepository;
    private final StockPriceService stockPriceService;
    private final AccountRepository accountRepository;
    private final AtrService atrService;
    
    public List<PortfolioResponse> getClientPortfolio(Long clientId) {
        List<Portfolio> portfolios = portfolioRepository.findByClientId(clientId);

        // Parallel stream: fetches current prices + ATR for all holdings concurrently
        // instead of sequentially, cutting wall-clock time from O(N × latency) → O(latency).
        return portfolios.parallelStream()
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
        BigDecimal currentPrice;
        try {
            currentPrice = stockPriceService.getCurrentPrice(portfolio.getSymbol());
        } catch (Exception e) {
            log.warn("Price fetch failed for {}: {} — using average price as fallback", portfolio.getSymbol(), e.getMessage());
            currentPrice = portfolio.getAveragePrice();
        }
        BigDecimal totalValue = currentPrice.multiply(BigDecimal.valueOf(portfolio.getQuantity()));
        BigDecimal investedValue = portfolio.getAveragePrice().multiply(BigDecimal.valueOf(portfolio.getQuantity()));
        BigDecimal profitLoss = totalValue.subtract(investedValue);
        BigDecimal profitLossPercent = investedValue.compareTo(BigDecimal.ZERO) > 0
            ? profitLoss.divide(investedValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;
        
        // Weight updates are now handled by the batch scheduler for better performance
        // No blocking operations during portfolio page load

        // ATR-14 + percentiles: approximated from 5-min close history (daily high/low = intraday max/min)
        // Wrapped in try/catch so a throttle or data exception on one symbol never fails the whole portfolio load.
        AtrService.AtrResult atrResult = null;
        try {
            atrResult = atrService.computeAtrResult(portfolio.getSymbol());
        } catch (Exception e) {
            log.warn("ATR computation failed for {}: {}", portfolio.getSymbol(), e.getMessage());
        }
        BigDecimal atr14 = atrResult != null ? atrResult.atr14() : null;
        BigDecimal atr75 = atrResult != null ? atrResult.atr75() : null;
        BigDecimal atr90 = atrResult != null ? atrResult.atr90() : null;

        BigDecimal postMarketPrice = null;
        try {
            postMarketPrice = stockPriceService.getPostMarketPrice(portfolio.getSymbol());
        } catch (Exception e) {
            log.debug("Post-market price fetch failed for {}: {}", portfolio.getSymbol(), e.getMessage());
        }

        return new PortfolioResponse(
            portfolio.getId(),
            portfolio.getSymbol(),
            portfolio.getQuantity(),
            portfolio.getAveragePrice(),
            currentPrice,
            totalValue,
            profitLoss,
            profitLossPercent,
            atr14,
            atr75,
            atr90,
            postMarketPrice
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
