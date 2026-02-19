package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.AccountResponse;
import com.example.stockbrokerage.service.AccountService;
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
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200", "http://localhost:4201", "http://127.0.0.1:4201"})
@Tag(name = "Accounts", description = "Client cash account operations (fund / withdraw)")
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/client/{clientId}")
    @Operation(
        summary = "Get account",
        description = "Returns cash balance, reserved balance (pending orders), and available balance for a client."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account details returned",
            content = @Content(schema = @Schema(implementation = AccountResponse.class))),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<AccountResponse> getClientAccount(
            @Parameter(description = "Client ID", example = "1") @PathVariable Long clientId) {
        return ResponseEntity.ok(accountService.getClientAccount(clientId));
    }

    @PostMapping("/client/{clientId}/add-funds")
    @Operation(
        summary = "Add funds",
        description = "Deposit cash into a client account. Body: `{ \"amount\": 5000.00 }`"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Funds added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid amount", content = @Content),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<Void> addFunds(
            @Parameter(description = "Client ID", example = "1") @PathVariable Long clientId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Amount to deposit",
                content = @Content(schema = @Schema(example = "{\"amount\": 5000.00}")))
            @RequestBody Map<String, BigDecimal> request) {
        accountService.addFunds(clientId, request.get("amount"));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/client/{clientId}/withdraw-funds")
    @Operation(
        summary = "Withdraw funds",
        description = "Withdraw cash from a client account. Body: `{ \"amount\": 1000.00 }`"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Funds withdrawn successfully"),
        @ApiResponse(responseCode = "400", description = "Insufficient funds or invalid amount", content = @Content),
        @ApiResponse(responseCode = "404", description = "Client not found", content = @Content)
    })
    public ResponseEntity<Void> withdrawFunds(
            @Parameter(description = "Client ID", example = "1") @PathVariable Long clientId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Amount to withdraw",
                content = @Content(schema = @Schema(example = "{\"amount\": 1000.00}")))
            @RequestBody Map<String, BigDecimal> request) {
        accountService.withdrawFunds(clientId, request.get("amount"));
        return ResponseEntity.ok().build();
    }
}
