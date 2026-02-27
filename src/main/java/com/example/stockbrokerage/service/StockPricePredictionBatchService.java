package com.example.stockbrokerage.service;

import com.example.stockbrokerage.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Hourly batch job that:
 *  1. Resolves past predictions (fills in actual prices, updates per-stock weights)
 *  2. Calculates fresh predictions for the next 8 hours for every portfolio holding
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockPricePredictionBatchService {

    private final PortfolioRepository          portfolioRepository;
    private final StockPricePredictionService  predictionService;
    private final MarketIndexService           marketIndexService;

    /**
     * Runs every hour on the hour. Initial delay of 30 seconds after startup.
     * fixedDelay ensures the previous run has finished before the next starts.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 30_000)
    public void runHourlyPredictionBatch() {
        log.info("=== Starting hourly stock price prediction batch ===");

        List<String> symbols = portfolioRepository.findAll().stream()
            .map(p -> p.getSymbol())
            .distinct()
            .toList();

        if (symbols.isEmpty()) {
            log.info("No portfolio symbols found – skipping prediction batch");
            return;
        }

        log.info("Running predictions for {} symbols: {}", symbols.size(), symbols);

        // Step 0: Refresh index correlations for all symbols (sequential – lightweight)
        log.info("Refreshing market-index correlations for {} symbols", symbols.size());
        for (String symbol : symbols) {
            try {
                marketIndexService.refreshCorrelations(symbol);
            } catch (Exception e) {
                log.warn("Index correlation refresh failed for {}: {}", symbol, e.getMessage());
            }
        }

        // Step 1: Resolve past predictions and update weights (sequential to avoid DB conflicts)
        for (String symbol : symbols) {
            try {
                predictionService.resolveAndUpdateWeights(symbol);
            } catch (Exception e) {
                log.error("Error resolving predictions for {}: {}", symbol, e.getMessage());
            }
        }

        // Step 2: Calculate new predictions sequentially with a 1-second pause between symbols.
        // Previous parallel scatter hit all 58 symbols at once, immediately saturating
        // StockPricePredictionService's rate limiter (4/s). Sequential approach takes ~1 min
        // for 58 symbols — well within the 60-minute batch window.
        for (String symbol : symbols) {
            try {
                predictionService.calculateAndStore(symbol);
                log.debug("Completed price predictions for {}", symbol);
            } catch (Exception e) {
                log.error("Error calculating predictions for {}: {}", symbol, e.getMessage());
            }
            try {
                Thread.sleep(1_000); // 1 s between stocks — respects rate limiter
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Prediction batch interrupted after symbol {}", symbol);
                break;
            }
        }

        log.info("=== Hourly prediction batch complete for {} symbols ===", symbols.size());
    }
}
