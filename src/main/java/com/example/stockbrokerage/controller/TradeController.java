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
@Tag(name = "Trade Management", description = "APIs for managing stock trades")
public class TradeController {
    
    private final TradeService tradeService;
    
    @PostMapping
    @Operation(summary = "Execute a new trade", description = "Submit a new trade for execution with fraud checks and rule validation")
    public ResponseEntity<TradeResponse> executeTrade(@Valid @RequestBody TradeRequest request) {
        TradeResponse response = tradeService.executeTrade(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get trade by ID", description = "Retrieve trade details by trade ID")
    public ResponseEntity<TradeResponse> getTradeById(@PathVariable Long id) {
        TradeResponse response = tradeService.getTradeById(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @Operation(summary = "Get all trades", description = "Retrieve all trades in the system")
    public ResponseEntity<List<TradeResponse>> getAllTrades() {
        List<TradeResponse> trades = tradeService.getAllTrades();
        return ResponseEntity.ok(trades);
    }
    
    @GetMapping("/client/{clientId}")
    @Operation(summary = "Get trades by client", description = "Retrieve all trades for a specific client")
    public ResponseEntity<List<TradeResponse>> getTradesByClient(@PathVariable Long clientId) {
        List<TradeResponse> trades = tradeService.getTradesByClient(clientId);
        return ResponseEntity.ok(trades);
    }
    
    @GetMapping("/symbol/{symbol}")
    @Operation(summary = "Get trades by symbol", description = "Retrieve all trades for a specific stock symbol")
    public ResponseEntity<List<TradeResponse>> getTradesBySymbol(@PathVariable String symbol) {
        List<TradeResponse> trades = tradeService.getTradesBySymbol(symbol);
        return ResponseEntity.ok(trades);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel trade", description = "Cancel a pending trade")
    public ResponseEntity<Void> cancelTrade(@PathVariable Long id) {
        tradeService.cancelTrade(id);
        return ResponseEntity.noContent().build();
    }
}
