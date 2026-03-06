package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.SwingTradeSuggestionResponse;
import com.example.stockbrokerage.dto.SwingTradeSuccessRateResponse;
import com.example.stockbrokerage.service.SwingTradeService;
import com.example.stockbrokerage.service.SwingTradeTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/swing-trades")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Swing Trades", description = "Multi-day swing trade predictions using 5 technical strategies with adaptive weights")
@CrossOrigin(origins = "*")
public class SwingTradeController {

    private final SwingTradeService         swingTradeService;
    private final SwingTradeTrackingService trackingService;

    @GetMapping("/suggestions/{clientId}")
    @Operation(
        summary = "Get swing trade suggestions for a client",
        description = "Analyses held stocks using RSI(14), MACD(12/26/9), Bollinger Bands(20), "
            + "EMA Crossover(9/21) and Volume Momentum. Returns up to 5 suggestions ordered by "
            + "highest potential return. All timestamps are Eastern Time."
    )
    public ResponseEntity<List<SwingTradeSuggestionResponse>> getSuggestions(@PathVariable Long clientId) {
        log.info("Generating swing trade suggestions for client {}", clientId);
        List<SwingTradeSuggestionResponse> suggestions = swingTradeService.getSwingTradeSuggestions(clientId);
        try {
            trackingService.saveSuggestions(clientId, suggestions);
        } catch (Exception e) {
            log.warn("Failed to persist swing suggestions for client {}: {}", clientId, e.getMessage());
        }
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/{clientId}/history")
    @Operation(
        summary = "Get recent swing trade suggestion history",
        description = "Returns swing trade suggestions from the last 5 days for the given client, newest first."
    )
    public ResponseEntity<List<SwingTradeSuggestionResponse>> getHistory(@PathVariable Long clientId) {
        return ResponseEntity.ok(trackingService.getRecentHistory(clientId));
    }

    @GetMapping("/{clientId}/success-rate")
    @Operation(
        summary = "Get swing trade success-rate statistics",
        description = "Returns aggregate success/failure counts and success percentage for all resolved swing predictions."
    )
    public ResponseEntity<SwingTradeSuccessRateResponse> getSuccessRate(@PathVariable Long clientId) {
        return ResponseEntity.ok(trackingService.getSuccessRate(clientId));
    }
}
