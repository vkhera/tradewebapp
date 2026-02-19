package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.service.StockPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200", "http://localhost:4201", "http://127.0.0.1:4201"})
@Tag(name = "Stocks", description = "Real-time stock price and quote lookup")
public class StockController {

    private final StockPriceService stockPriceService;

    @GetMapping("/price/{symbol}")
    @Operation(
        summary = "Get current price",
        description = "Returns the latest price for a stock symbol. Response: `{ \"symbol\": \"AAPL\", \"price\": 175.34 }`"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current price",
            content = @Content(schema = @Schema(example = "{\"symbol\":\"AAPL\",\"price\":175.34}"))),
        @ApiResponse(responseCode = "404", description = "Symbol not found", content = @Content)
    })
    public ResponseEntity<Map<String, Object>> getStockPrice(
            @Parameter(description = "Stock ticker symbol", example = "AAPL") @PathVariable String symbol) {
        BigDecimal price = stockPriceService.getCurrentPrice(symbol);
        return ResponseEntity.ok(Map.of("symbol", symbol, "price", price));
    }

    @GetMapping("/quote/{symbol}")
    @Operation(
        summary = "Get full quote",
        description = "Returns an extended quote including open, high, low, close, volume and bid/ask where available."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Full quote data"),
        @ApiResponse(responseCode = "404", description = "Symbol not found", content = @Content)
    })
    public ResponseEntity<Map<String, Object>> getStockQuote(
            @Parameter(description = "Stock ticker symbol", example = "AAPL") @PathVariable String symbol) {
        return ResponseEntity.ok(stockPriceService.getQuote(symbol));
    }
}
