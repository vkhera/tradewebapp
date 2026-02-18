package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.PortfolioResponse;
import com.example.stockbrokerage.dto.PortfolioSummaryResponse;
import com.example.stockbrokerage.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class PortfolioController {
    
    private final PortfolioService portfolioService;
    
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<PortfolioResponse>> getClientPortfolio(@PathVariable Long clientId) {
        return ResponseEntity.ok(portfolioService.getClientPortfolio(clientId));
    }
    
    @GetMapping("/client/{clientId}/summary")
    public ResponseEntity<PortfolioSummaryResponse> getClientPortfolioSummary(@PathVariable Long clientId) {
        return ResponseEntity.ok(portfolioService.getClientPortfolioSummary(clientId));
    }
}
