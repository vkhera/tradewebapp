package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.AccountResponse;
import com.example.stockbrokerage.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class AccountController {
    
    private final AccountService accountService;
    
    @GetMapping("/client/{clientId}")
    public ResponseEntity<AccountResponse> getClientAccount(@PathVariable Long clientId) {
        return ResponseEntity.ok(accountService.getClientAccount(clientId));
    }
    
    @PostMapping("/client/{clientId}/add-funds")
    public ResponseEntity<Void> addFunds(@PathVariable Long clientId, @RequestBody Map<String, BigDecimal> request) {
        BigDecimal amount = request.get("amount");
        accountService.addFunds(clientId, amount);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/client/{clientId}/withdraw-funds")
    public ResponseEntity<Void> withdrawFunds(@PathVariable Long clientId, @RequestBody Map<String, BigDecimal> request) {
        BigDecimal amount = request.get("amount");
        accountService.withdrawFunds(clientId, amount);
        return ResponseEntity.ok().build();
    }
}
