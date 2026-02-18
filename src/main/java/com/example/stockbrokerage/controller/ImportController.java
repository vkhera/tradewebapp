package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.HoldingImportRequest;
import com.example.stockbrokerage.dto.ActivityImportRequest;
import com.example.stockbrokerage.dto.CleanupRequest;
import com.example.stockbrokerage.dto.ImportResponse;
import com.example.stockbrokerage.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Import", description = "Portfolio and Activity Import API")
@CrossOrigin(origins = "*")
public class ImportController {
    
    private final ImportService importService;
    
    @PostMapping("/holdings")
    @Operation(summary = "Import holdings from CSV file")
    public ResponseEntity<ImportResponse> importHoldings(@RequestBody HoldingImportRequest request) {
        log.info("Importing holdings for client {} from file {}", request.getClientId(), request.getFileName());
        ImportResponse response = importService.importHoldings(request.getClientId(), request.getFileName());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/activity")
    @Operation(summary = "Import activity from CSV file")
    public ResponseEntity<ImportResponse> importActivity(@RequestBody ActivityImportRequest request) {
        log.info("Importing activity for client {} from file {}", request.getClientId(), request.getFileName());
        ImportResponse response = importService.importActivity(request.getClientId(), request.getFileName());
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/cleanup")
    @Operation(summary = "Clean up portfolio and activity for a client")
    public ResponseEntity<ImportResponse> cleanupClientData(@RequestBody CleanupRequest request) {
        log.info("Cleaning up data for client {}", request.getClientId());
        ImportResponse response = importService.cleanupClientData(request.getClientId());
        return ResponseEntity.ok(response);
    }
}
