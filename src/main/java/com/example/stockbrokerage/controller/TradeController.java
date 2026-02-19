package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.TradeRequest;
import com.example.stockbrokerage.dto.TradeResponse;
import com.example.stockbrokerage.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200", "http://localhost:4201", "http://127.0.0.1:4201"})
@Tag(name = "Trades", description = "Order submission, status and history")
public class TradeController {
    
    private final TradeService tradeService;
    
    @PostMapping
    @Operation(
        summary = "Place order",
        description = "Submit a BUY or SELL order. The order passes through fraud-detection rules (Drools) and cash-validation rules before execution."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Order placed",
            content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TradeResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed (e.g. insufficient funds, rule rejected)",
            content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Fraud detected",
            content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<TradeResponse> executeTrade(@Valid @RequestBody TradeRequest request) {
        TradeResponse response = tradeService.executeTrade(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get trade by ID", description = "Retrieve a single trade by its ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trade found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Trade not found",
            content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<TradeResponse> getTradeById(
            @io.swagger.v3.oas.annotations.Parameter(description = "Trade ID", example = "1") @PathVariable Long id) {
        TradeResponse response = tradeService.getTradeById(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @Operation(summary = "Get all trades", description = "Retrieve all trades for the authenticated user's accessible scope.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trade list")
    public ResponseEntity<List<TradeResponse>> getAllTrades() {
        List<TradeResponse> trades = tradeService.getAllTrades();
        return ResponseEntity.ok(trades);
    }
    
    @GetMapping("/client/{clientId}")
    @Operation(summary = "Get trades by client", description = "Retrieve all trades for a specific client.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trade list for client")
    public ResponseEntity<List<TradeResponse>> getTradesByClient(
            @io.swagger.v3.oas.annotations.Parameter(description = "Client ID", example = "1") @PathVariable Long clientId) {
        List<TradeResponse> trades = tradeService.getTradesByClient(clientId);
        return ResponseEntity.ok(trades);
    }
    
    @GetMapping("/symbol/{symbol}")
    @Operation(summary = "Get trades by symbol", description = "Retrieve all trades for a given ticker symbol.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trade list for symbol")
    public ResponseEntity<List<TradeResponse>> getTradesBySymbol(
            @io.swagger.v3.oas.annotations.Parameter(description = "Ticker symbol", example = "AAPL") @PathVariable String symbol) {
        List<TradeResponse> trades = tradeService.getTradesBySymbol(symbol);
        return ResponseEntity.ok(trades);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel trade", description = "Cancel a pending/open trade. Only trades in PENDING status can be cancelled.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Trade cancelled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Trade not found",
            content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Trade cannot be cancelled (already executed)",
            content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<Void> cancelTrade(
            @io.swagger.v3.oas.annotations.Parameter(description = "Trade ID", example = "1") @PathVariable Long id) {
        tradeService.cancelTrade(id);
        return ResponseEntity.noContent().build();
    }
}
