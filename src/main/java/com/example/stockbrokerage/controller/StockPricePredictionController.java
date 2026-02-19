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
@Tag(name = "Price Predictions", description = "8-hour hourly price predictions using 5-min Yahoo Finance bars")
@CrossOrigin(origins = "*")
public class StockPricePredictionController {

    private final StockPricePredictionService predictionService;

    @GetMapping("/{symbol}")
    @Operation(
        summary = "Get price predictions (cached)",
        description = """
            Returns 8 hourly price predictions for the next 8 hours using a weighted ensemble of 5 techniques:\n
            - **Linear Regression** – OLS trend extrapolation over 480 bars (40h) of 5-min data\n
            - **EMA Extrapolation** – Double EMA with 144-bar (12h) span\n
            - **Momentum** – Rate-of-change over the last 60 bars (5h)\n
            - **Mean Reversion** – Bollinger-band pull toward 240-bar (20h) mean\n
            - **Holt-Winters** – Double exponential smoothing (level + trend)\n
            \n
            Weights adapt per stock over time: error < 2% → weight × 1.15; error ≥ 2% → weight × 0.85.\n
            Served from cache (50-min TTL). Use the refresh endpoint to force recalculation.
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "8-hour prediction set (cached=true if from cache)",
            content = @io.swagger.v3.oas.annotations.media.Content(
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = StockPricePredictionResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Symbol not recognised",
            content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<StockPricePredictionResponse> getPredictions(
            @io.swagger.v3.oas.annotations.Parameter(description = "Ticker symbol (case-insensitive)", example = "AAPL")
            @PathVariable String symbol) {
        log.debug("Getting predictions for {}", symbol.toUpperCase());
        StockPricePredictionResponse response = predictionService.getPredictions(symbol.toUpperCase());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{symbol}/refresh")
    @Operation(
        summary = "Refresh predictions (force recalculate)",
        description = "Bypasses the cache, fetches fresh 5-min bars from Yahoo Finance, and recalculates all 5 technique predictions. Also resolves any past predictions and updates adaptive weights. Slower than GET – intended for manual refresh or testing."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Freshly calculated predictions (cached=false)")
    public ResponseEntity<StockPricePredictionResponse> refreshPredictions(
            @io.swagger.v3.oas.annotations.Parameter(description = "Ticker symbol (case-insensitive)", example = "AAPL")
            @PathVariable String symbol) {
        log.info("Forcing prediction refresh for {}", symbol.toUpperCase());
        StockPricePredictionResponse response = predictionService.calculateAndStore(symbol.toUpperCase());
        return ResponseEntity.ok(response);
    }
}
