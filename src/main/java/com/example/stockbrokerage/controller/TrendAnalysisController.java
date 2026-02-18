package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.TrendAnalysisRequest;
import com.example.stockbrokerage.dto.TrendPrediction;
import com.example.stockbrokerage.service.TrendAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trends")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Trend Analysis", description = "Stock Trend Analysis API")
@CrossOrigin(origins = "*")
public class TrendAnalysisController {
    
    private final TrendAnalysisService trendAnalysisService;
    
    @PostMapping("/analyze")
    @Operation(summary = "Analyze stock trend using multiple techniques")
    public ResponseEntity<TrendPrediction> analyzeTrend(@RequestBody TrendAnalysisRequest request) {
        log.info("Analyzing trend for symbol: {}", request.getSymbol());
        TrendPrediction prediction = trendAnalysisService.analyzeTrend(request.getSymbol());
        return ResponseEntity.ok(prediction);
    }
    
    @GetMapping("/analyze/{symbol}")
    @Operation(summary = "Analyze stock trend by symbol")
    public ResponseEntity<TrendPrediction> analyzeTrendBySymbol(@PathVariable String symbol) {
        log.info("Analyzing trend for symbol: {}", symbol);
        TrendPrediction prediction = trendAnalysisService.analyzeTrend(symbol);
        return ResponseEntity.ok(prediction);
    }
    
    @GetMapping("/last/{symbol}")
    @Operation(summary = "Get last cached trend prediction for fast loading (no recalculation)")
    public ResponseEntity<TrendPrediction> getLastPrediction(@PathVariable String symbol) {
        log.debug("Fetching last cached trend for symbol: {}", symbol);
        TrendPrediction prediction = trendAnalysisService.getLastPrediction(symbol);
        if (prediction == null) {
            // If no cached prediction exists, calculate it once
            log.info("No cached prediction for {}, calculating...", symbol);
            prediction = trendAnalysisService.analyzeTrend(symbol);
        }
        return ResponseEntity.ok(prediction);
    }
}
