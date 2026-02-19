package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.PortfolioResponse;
import com.example.stockbrokerage.dto.PortfolioSummaryResponse;
import com.example.stockbrokerage.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200", "http://localhost:4201", "http://127.0.0.1:4201"})
@Tag(name = "Portfolio", description = "Portfolio holdings, P/L and summary")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/client/{clientId}")
    @Operation(
        summary = "Get holdings",
        description = "Returns all current stock holdings for a client with quantity, average cost, current price, and unrealised P/L."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Holdings list",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PortfolioResponse.class)))),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<List<PortfolioResponse>> getClientPortfolio(
            @Parameter(description = "Client ID", example = "1") @PathVariable Long clientId) {
        return ResponseEntity.ok(portfolioService.getClientPortfolio(clientId));
    }

    @GetMapping("/client/{clientId}/summary")
    @Operation(
        summary = "Get portfolio summary",
        description = "Returns aggregated portfolio stats: total invested value, current value, total P/L, cash balances, and per-holding breakdown."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Portfolio summary",
            content = @Content(schema = @Schema(implementation = PortfolioSummaryResponse.class))),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<PortfolioSummaryResponse> getClientPortfolioSummary(
            @Parameter(description = "Client ID", example = "1") @PathVariable Long clientId) {
        return ResponseEntity.ok(portfolioService.getClientPortfolioSummary(clientId));
    }
}
