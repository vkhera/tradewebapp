package com.example.stockbrokerage.service;

import com.example.stockbrokerage.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

        // Step 1: Resolve past predictions and update weights (sequential to avoid DB conflicts)
        for (String symbol : symbols) {
            try {
                predictionService.resolveAndUpdateWeights(symbol);
            } catch (Exception e) {
                log.error("Error resolving predictions for {}: {}", symbol, e.getMessage());
            }
        }

        // Step 2: Calculate new predictions in parallel
        List<CompletableFuture<Void>> futures = symbols.stream()
            .map(symbol -> CompletableFuture.runAsync(() -> {
                try {
                    predictionService.calculateAndStore(symbol);
                    log.debug("Completed price predictions for {}", symbol);
                } catch (Exception e) {
                    log.error("Error calculating predictions for {}: {}", symbol, e.getMessage());
                }
            }))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("=== Hourly prediction batch complete for {} symbols ===", symbols.size());
    }
}
