package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.HoldingImportRequest;
import com.example.stockbrokerage.dto.ActivityImportRequest;
import com.example.stockbrokerage.dto.CleanupRequest;
import com.example.stockbrokerage.dto.ImportResponse;
import com.example.stockbrokerage.entity.User;
import com.example.stockbrokerage.repository.UserRepository;
import com.example.stockbrokerage.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Import", description = "Bulk CSV import of holdings and activity")
@CrossOrigin(origins = "*")
public class ImportController {
    
    private final ImportService importService;
    private final UserRepository userRepository;

    /**
     * Verifies the currently authenticated user is allowed to operate on the given clientId.
     * ADMIN users can act on any clientId; CLIENT users can only act on their own.
     */
    private void checkAuthorized(Long clientId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (user.getRole() == User.Role.ADMIN) {
            return; // admins may access any client
        }
        if (user.getClient() == null || !user.getClient().getId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You are not authorized to perform this action for client " + clientId);
        }
    }
    
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
        checkAuthorized(request.getClientId());
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
        checkAuthorized(request.getClientId());
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
        checkAuthorized(request.getClientId());
        log.info("Cleaning up data for client {}", request.getClientId());
        ImportResponse response = importService.cleanupClientData(request.getClientId());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload a CSV file to the import directory",
        description = "Saves the uploaded file into the server-side importexport directory so it can be referenced by the holdings or activity import endpoints."
    )
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No filename provided"));
        }
        java.nio.file.Path dest = Paths.get("importexport", filename);
        Files.createDirectories(dest.getParent());
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("Uploaded file to importexport: {}", filename);
        return ResponseEntity.ok(Map.of("fileName", filename));
    }
}
