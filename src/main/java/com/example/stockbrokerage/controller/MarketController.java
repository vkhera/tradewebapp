package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.MarketIndexQuote;
import com.example.stockbrokerage.dto.MarketStatusResponse;
import com.example.stockbrokerage.service.MarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200", "http://localhost:4201", "http://127.0.0.1:4201"})
@Tag(name = "Market", description = "Real-time market status and index quotes")
public class MarketController {

    private final MarketService marketService;

    @GetMapping("/status")
    @Operation(
        summary = "Current market status",
        description = "Returns the current Eastern Time and NYSE session status " +
                      "(OPEN, PRE_MARKET, POST_MARKET, or CLOSED)."
    )
    public ResponseEntity<MarketStatusResponse> getMarketStatus() {
        return ResponseEntity.ok(marketService.getMarketStatus());
    }

    @GetMapping("/indices")
    @Operation(
        summary = "Major index quotes",
        description = "Returns snapshot quotes for S&P 500, Dow Jones, Nasdaq, Gold, and Russell 2K. " +
                      "Results are cached for 1 minute to avoid rate-limiting."
    )
    public ResponseEntity<List<MarketIndexQuote>> getIndices() {
        return ResponseEntity.ok(marketService.getIndices());
    }
}
