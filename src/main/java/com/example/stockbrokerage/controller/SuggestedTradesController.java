package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.SuggestedTradeHistoryResponse;
import com.example.stockbrokerage.dto.SuggestedTradeResponse;
import com.example.stockbrokerage.dto.TradeSuccessRateResponse;
import com.example.stockbrokerage.service.SuggestedTradesService;
import com.example.stockbrokerage.service.SuggestedTradeTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Suggested Trades", description = "AI-powered trade suggestions based on ATR and price predictions")
@CrossOrigin(origins = "*")
public class SuggestedTradesController {

    private final SuggestedTradesService        suggestedTradesService;
    private final SuggestedTradeTrackingService trackingService;

    @GetMapping("/{clientId}")
    @Operation(
        summary = "Get suggested trades for a client",
        description = "Analyses the client's holdings using ATR(14) and hourly price predictions. "
            + "Returns up to 5 sell-and-buy-back suggestions for stocks expected to decline more than 2% "
            + "in the next 8 hours."
    )
    public ResponseEntity<List<SuggestedTradeResponse>> getSuggestions(@PathVariable Long clientId) {
        log.info("Generating trade suggestions for client {}", clientId);
        List<SuggestedTradeResponse> suggestions = suggestedTradesService.getSuggestedTrades(clientId);
        trackingService.saveSuggestions(clientId, suggestions);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/{clientId}/history")
    @Operation(
        summary = "Get recent suggestion history",
        description = "Returns the last 5 days of trade suggestions for the given client, newest first."
    )
    public ResponseEntity<List<SuggestedTradeHistoryResponse>> getHistory(@PathVariable Long clientId) {
        return ResponseEntity.ok(trackingService.getRecentHistory(clientId));
    }

    @GetMapping("/{clientId}/success-rate")
    @Operation(
        summary = "Get suggestion success-rate statistics",
        description = "Returns aggregate success/failure/pending counts and success percentage for the client."
    )
    public ResponseEntity<TradeSuccessRateResponse> getSuccessRate(@PathVariable Long clientId) {
        return ResponseEntity.ok(trackingService.getSuccessRate(clientId));
    }
}
