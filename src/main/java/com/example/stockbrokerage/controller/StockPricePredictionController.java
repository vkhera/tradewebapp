package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.StockPricePredictionResponse;
import com.example.stockbrokerage.service.StockPricePredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stock Price Prediction", description = "Hourly stock price prediction API")
@CrossOrigin(origins = "*")
public class StockPricePredictionController {

    private final StockPricePredictionService predictionService;

    @GetMapping("/{symbol}")
    @Operation(summary = "Get cached 8-hour price predictions for a stock (fast – from cache)")
    public ResponseEntity<StockPricePredictionResponse> getPredictions(@PathVariable String symbol) {
        log.debug("Getting predictions for {}", symbol.toUpperCase());
        StockPricePredictionResponse response = predictionService.getPredictions(symbol.toUpperCase());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{symbol}/refresh")
    @Operation(summary = "Force recalculate predictions for a stock (slower – fresh data)")
    public ResponseEntity<StockPricePredictionResponse> refreshPredictions(@PathVariable String symbol) {
        log.info("Forcing prediction refresh for {}", symbol.toUpperCase());
        StockPricePredictionResponse response = predictionService.calculateAndStore(symbol.toUpperCase());
        return ResponseEntity.ok(response);
    }
}
