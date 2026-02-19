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
@Tag(name = "Trend Analysis", description = "Multi-technique stock trend analysis with adaptive per-stock weights")
@CrossOrigin(origins = "*")
public class TrendAnalysisController {
    
    private final TrendAnalysisService trendAnalysisService;
    
    @PostMapping("/analyze")
    @Operation(
        summary = "Analyze trend (body)",
        description = "Force a fresh trend analysis using 5 techniques (MA Crossover, RSI, MACD, Price Momentum, Volume Trend). Returns UPTREND / DOWNTREND / SIDEWAYS with per-technique confidence scores and adaptive weights."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trend prediction produced")
    public ResponseEntity<TrendPrediction> analyzeTrend(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Symbol to analyze") @RequestBody TrendAnalysisRequest request) {
        log.info("Analyzing trend for symbol: {}", request.getSymbol());
        TrendPrediction prediction = trendAnalysisService.analyzeTrend(request.getSymbol());
        return ResponseEntity.ok(prediction);
    }
    
    @GetMapping("/analyze/{symbol}")
    @Operation(
        summary = "Analyze trend (path)",
        description = "Same as POST /analyze but accepts the symbol as a path parameter. Forces fresh calculation."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trend prediction")
    public ResponseEntity<TrendPrediction> analyzeTrendBySymbol(
            @io.swagger.v3.oas.annotations.Parameter(description = "Ticker symbol", example = "AAPL") @PathVariable String symbol) {
        log.info("Analyzing trend for symbol: {}", symbol);
        TrendPrediction prediction = trendAnalysisService.analyzeTrend(symbol);
        return ResponseEntity.ok(prediction);
    }
    
    @GetMapping("/last/{symbol}")
    @Operation(
        summary = "Get cached trend (fast)",
        description = "Returns the last computed trend prediction from cache without recalculating. Used by the portfolio page for fast load. Falls back to a fresh calculation only if no cache exists. Batch scheduler refreshes cache every day at 4:30 PM ET."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cached or freshly computed trend prediction")
    public ResponseEntity<TrendPrediction> getLastPrediction(
            @io.swagger.v3.oas.annotations.Parameter(description = "Ticker symbol", example = "AAPL") @PathVariable String symbol) {
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
