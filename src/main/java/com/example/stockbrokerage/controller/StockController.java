package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class StockController {
    
    private final StockPriceService stockPriceService;
    
    @GetMapping("/price/{symbol}")
    public ResponseEntity<Map<String, Object>> getStockPrice(@PathVariable String symbol) {
        BigDecimal price = stockPriceService.getCurrentPrice(symbol);
        return ResponseEntity.ok(Map.of("symbol", symbol, "price", price));
    }
    
    @GetMapping("/quote/{symbol}")
    public ResponseEntity<Map<String, Object>> getStockQuote(@PathVariable String symbol) {
        return ResponseEntity.ok(stockPriceService.getQuote(symbol));
    }
}
