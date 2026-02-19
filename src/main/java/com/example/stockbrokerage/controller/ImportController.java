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
@Tag(name = "Import", description = "Bulk CSV import of holdings and activity")
@CrossOrigin(origins = "*")
public class ImportController {
    
    private final ImportService importService;
    
    @PostMapping("/holdings")
    @Operation(
        summary = "Import holdings from CSV",
        description = "Reads a Schwab-format holdings CSV file from the server filesystem path provided in the request body, imports positions for the given client, and returns a summary of records imported/skipped."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Import complete"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "File not found or parse error",
            content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ImportResponse> importHoldings(@RequestBody HoldingImportRequest request) {
        log.info("Importing holdings for client {} from file {}", request.getClientId(), request.getFileName());
        ImportResponse response = importService.importHoldings(request.getClientId(), request.getFileName());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/activity")
    @Operation(
        summary = "Import activity from CSV",
        description = "Reads a Schwab-format activity/transaction CSV file, creates trade records for the given client, and returns a summary. IIAXX (cash sweep) entries are skipped automatically. Symbol is extracted from the first word of the Description column."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Import complete"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "File not found or parse error",
            content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ImportResponse> importActivity(@RequestBody ActivityImportRequest request) {
        log.info("Importing activity for client {} from file {}", request.getClientId(), request.getFileName());
        ImportResponse response = importService.importActivity(request.getClientId(), request.getFileName());
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/cleanup")
    @Operation(
        summary = "Cleanup client data",
        description = "Deletes all portfolio holdings and trade activity for the given client. Used to reset before a fresh CSV import. **Destructive – cannot be undone.**"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Client data cleared"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Client not found",
            content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<ImportResponse> cleanupClientData(@RequestBody CleanupRequest request) {
        log.info("Cleaning up data for client {}", request.getClientId());
        ImportResponse response = importService.cleanupClientData(request.getClientId());
        return ResponseEntity.ok(response);
    }
}
