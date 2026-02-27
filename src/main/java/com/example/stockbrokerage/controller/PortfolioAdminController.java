package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.ClientHoldingResponse;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.repository.PortfolioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/portfolio")
@RequiredArgsConstructor
@Tag(name = "Admin – Portfolio", description = "Admin: view holdings across all clients")
public class PortfolioAdminController {

    private final PortfolioRepository portfolioRepository;

    @GetMapping("/all-holdings")
    @Operation(
        summary = "All client holdings",
        description = "Returns every portfolio row across all clients. Admin only."
    )
    public ResponseEntity<List<ClientHoldingResponse>> getAllHoldings() {
        List<ClientHoldingResponse> result = portfolioRepository.findAll().stream()
            .map(p -> new ClientHoldingResponse(
                p.getClient().getId(),
                p.getClient().getName(),
                p.getSymbol(),
                p.getQuantity()
            ))
            .sorted((a, b) -> {
                int cmp = a.getClientId().compareTo(b.getClientId());
                if (cmp != 0) return cmp;
                return a.getSymbol().compareTo(b.getSymbol());
            })
            .toList();
        return ResponseEntity.ok(result);
    }
}
