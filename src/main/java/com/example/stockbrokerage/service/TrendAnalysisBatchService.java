package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.TrendPrediction.TrendDirection;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendAnalysisBatchService {
    
    private final PortfolioRepository portfolioRepository;
    private final TrendAnalysisService trendAnalysisService;
    private final StockPriceService stockPriceService;
    
    /**
     * Runs trend analysis for all portfolio holdings every 10 minutes.
     * Uses parallel processing to analyze multiple stocks concurrently.
     * Also updates technique weights based on actual portfolio performance.
     */
    @Scheduled(fixedRate = 600000, initialDelay = 10000) // 10 minutes = 600000ms, start after 10 seconds
    public void runBatchTrendAnalysis() {
        log.info("Starting scheduled trend analysis batch job");
        
        try {
            // Get all portfolios grouped by symbol for weight updates
            List<Portfolio> allPortfolios = portfolioRepository.findAll();
            
            if (allPortfolios.isEmpty()) {
                log.info("No portfolio holdings found, skipping trend analysis");
                return;
            }
            
            // Group by symbol to calculate average performance
            Map<String, List<Portfolio>> portfoliosBySymbol = allPortfolios.stream()
                .collect(Collectors.groupingBy(Portfolio::getSymbol));
            
            log.info("Analyzing trends for {} unique symbols", portfoliosBySymbol.size());
            
            // Analyze trends and update weights in parallel
            List<CompletableFuture<Void>> futures = portfoliosBySymbol.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> {
                    String symbol = entry.getKey();
                    List<Portfolio> portfolios = entry.getValue();
                    
                    try {
                        // Calculate new trend prediction
                        trendAnalysisService.analyzeTrend(symbol);
                        
                        // Update weights based on actual performance
                        updateWeightsForSymbol(symbol, portfolios);
                        
                        log.debug("Completed trend analysis and weight update for {}", symbol);
                    } catch (Exception e) {
                        log.error("Error analyzing trend for {}: {}", symbol, e.getMessage());
                    }
                }))
                .toList();
            
            // Wait for all analyses to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            log.info("Completed scheduled trend analysis for {} symbols", portfoliosBySymbol.size());
            
        } catch (Exception e) {
            log.error("Error in batch trend analysis job", e);
        }
    }
    
    private void updateWeightsForSymbol(String symbol, List<Portfolio> portfolios) {
        try {
            // Calculate average P/L% across all holdings of this symbol
            BigDecimal totalProfitLossPercent = BigDecimal.ZERO;
            int validCount = 0;
            
            for (Portfolio portfolio : portfolios) {
                BigDecimal currentPrice = stockPriceService.getCurrentPrice(symbol);
                BigDecimal totalValue = currentPrice.multiply(BigDecimal.valueOf(portfolio.getQuantity()));
                BigDecimal investedValue = portfolio.getAveragePrice().multiply(BigDecimal.valueOf(portfolio.getQuantity()));
                
                if (investedValue.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal profitLoss = totalValue.subtract(investedValue);
                    BigDecimal profitLossPercent = profitLoss
                        .divide(investedValue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    
                    totalProfitLossPercent = totalProfitLossPercent.add(profitLossPercent);
                    validCount++;
                }
            }
            
            if (validCount > 0) {
                BigDecimal avgProfitLossPercent = totalProfitLossPercent
                    .divide(BigDecimal.valueOf(validCount), 4, RoundingMode.HALF_UP);
                
                TrendDirection actualTrend = determineActualTrend(avgProfitLossPercent);
                trendAnalysisService.updateWeights(symbol, actualTrend);
                
                log.debug("Updated weights for {} based on actual trend: {} (avg P/L: {}%)", 
                    symbol, actualTrend, avgProfitLossPercent);
            }
        } catch (Exception e) {
            log.warn("Could not update weights for {}: {}", symbol, e.getMessage());
        }
    }
    
    private TrendDirection determineActualTrend(BigDecimal profitLossPercent) {
        BigDecimal uptrendThreshold = BigDecimal.valueOf(2.0);
        BigDecimal downtrendThreshold = BigDecimal.valueOf(-2.0);
        
        if (profitLossPercent.compareTo(uptrendThreshold) >= 0) {
            return TrendDirection.UPTREND;
        } else if (profitLossPercent.compareTo(downtrendThreshold) <= 0) {
            return TrendDirection.DOWNTREND;
        } else {
            return TrendDirection.SIDEWAYS;
        }
    }
}
