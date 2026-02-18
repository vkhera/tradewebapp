package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.AccountResponse;
import com.example.stockbrokerage.entity.Account;
import com.example.stockbrokerage.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountService {
    
    private final AccountRepository accountRepository;
    
    public AccountResponse getClientAccount(Long clientId) {
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found for client: " + clientId));
        
        return new AccountResponse(
            account.getId(),
            account.getCashBalance(),
            account.getReservedBalance(),
            account.getAvailableBalance()
        );
    }
    
    @Transactional
    public void addFunds(Long clientId, BigDecimal amount) {
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        account.setCashBalance(account.getCashBalance().add(amount));
        accountRepository.save(account);
    }
    
    @Transactional
    public void withdrawFunds(Long clientId, BigDecimal amount) {
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient available balance for withdrawal");
        }
        
        account.setCashBalance(account.getCashBalance().subtract(amount));
        accountRepository.save(account);
    }
    
    @Transactional
    public void reserveFunds(Long clientId, BigDecimal amount) {
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient available balance");
        }
        
        account.setReservedBalance(account.getReservedBalance().add(amount));
        accountRepository.save(account);
    }
    
    @Transactional
    public void releaseReservedFunds(Long clientId, BigDecimal amount) {
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        account.setReservedBalance(account.getReservedBalance().subtract(amount));
        accountRepository.save(account);
    }
    
    @Transactional
    public void deductFunds(Long clientId, BigDecimal amount) {
        Account account = accountRepository.findByClientId(clientId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        account.setCashBalance(account.getCashBalance().subtract(amount));
        account.setReservedBalance(account.getReservedBalance().subtract(amount));
        accountRepository.save(account);
    }
}
