package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.SuggestedTradeResponse;
import com.example.stockbrokerage.service.SuggestedTradesService;
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

    private final SuggestedTradesService suggestedTradesService;

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
        return ResponseEntity.ok(suggestions);
    }
}
