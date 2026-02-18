package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.ImportResponse;
import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Account;
import com.example.stockbrokerage.repository.PortfolioRepository;
import com.example.stockbrokerage.repository.TradeRepository;
import com.example.stockbrokerage.repository.ClientRepository;
import com.example.stockbrokerage.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {
    
    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    
    private static final String IMPORT_DIRECTORY = "importexport";
    
    @Transactional
    public ImportResponse cleanupClientData(Long clientId) {
        ImportResponse response = new ImportResponse(false, "");
        
        try {
            log.info("Starting cleanup for client {}", clientId);
            
            // Delete all portfolio entries for the client
            List<Portfolio> portfolios = portfolioRepository.findByClientId(clientId);
            int portfolioCount = portfolios.size();
            portfolioRepository.deleteAll(portfolios);
            log.info("Deleted {} portfolio entries for client {}", portfolioCount, clientId);
            
            // Delete all trades for the client
            List<Trade> trades = tradeRepository.findByClientId(clientId);
            int tradeCount = trades.size();
            tradeRepository.deleteAll(trades);
            log.info("Deleted {} trade entries for client {}", tradeCount, clientId);
            
            response.setSuccess(true);
            response.setMessage("Cleanup completed successfully");
            response.setRecordsProcessed(portfolioCount + tradeCount);
            response.setRecordsImported(0);
            response.setRecordsSkipped(0);
            response.setErrors(new ArrayList<>());
            
        } catch (Exception e) {
            log.error("Error during cleanup for client {}", clientId, e);
            response.setMessage("Error during cleanup: " + e.getMessage());
        }
        
        return response;
    }
    
    @Transactional
    public ImportResponse importHoldings(Long clientId, String fileName) {
        ImportResponse response = new ImportResponse(false, "");
        List<String> errors = new ArrayList<>();
        int processed = 0;
        int imported = 0;
        int skipped = 0;
        
        try {
            Optional<Client> clientOpt = clientRepository.findById(clientId);
            if (!clientOpt.isPresent()) {
                response.setMessage("Client not found with ID: " + clientId);
                return response;
            }
            
            Client client = clientOpt.get();
            Account account = accountRepository.findByClientId(clientId)
                .orElseThrow(() -> new RuntimeException("Account not found for client: " + clientId));
            
            Path filePath = Paths.get(IMPORT_DIRECTORY, fileName);
            
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                String line;
                boolean dataSection = false;
                
                while ((line = reader.readLine()) != null) {
                    // Skip header lines until we find the data section
                    if (line.contains("Symbol Description")) {
                        dataSection = true;
                        continue;
                    }
                    
                    if (!dataSection || line.trim().isEmpty()) {
                        continue;
                    }
                    
                    processed++;
                    
                    try {
                        // Parse CSV line
                        String[] parts = parseCsvLine(line);
                        
                        if (parts.length < 9) {
                            // Check if this is the end of data (total/summary line or empty)
                            if (parts.length <= 2 || line.trim().toLowerCase().contains("total")) {
                                log.debug("Line {}: End of data section detected, stopping", processed);
                                break; // Stop processing, we've reached the end
                            }
                            skipped++;
                            String errorMsg = "Line %d: Insufficient data (only %d columns): %s".formatted(processed, parts.length, line);
                            errors.add(errorMsg);
                            log.warn(errorMsg);
                            continue;
                        }
                        
                        String symbolDescription = parts[1].trim();
                        String quantityStr = parts[2].trim();
                        String priceStr = parts[3].trim();
                        
                        if (symbolDescription.isEmpty() || quantityStr.isEmpty()) {
                            skipped++;
                            log.debug("Line {}: Skipping - empty symbol or quantity", processed);
                            continue;
                        }
                        
                        // Extract symbol (first word before space)
                        String symbol = symbolDescription.split(" ")[0].trim();
                        
                        // Handle cash - IIAXX represents cash/money market, skip it
                        if ("IIAXX".equalsIgnoreCase(symbol)) {
                            skipped++;
                            log.debug("Line {}: Skipping cash/money market (IIAXX) - this is expected", processed);
                            continue;
                        }
                        
                        // Validate symbol is not empty after extraction
                        if (symbol.isEmpty()) {
                            skipped++;
                            log.warn("Line {}: Empty symbol after extraction from '{}'", processed, symbolDescription);
                            continue;
                        }
                        
                        // Parse quantity
                        BigDecimal quantity = new BigDecimal(quantityStr.replace(",", ""));
                        
                        // Parse price (remove $ and commas)
                        BigDecimal price = new BigDecimal(priceStr.replace("$", "").replace(",", ""));
                        
                        log.debug("Processing line {}: Symbol={}, Quantity={}, Price={}", processed, symbol, quantity, price);
                        
                        // Check if portfolio entry already exists
                        Optional<Portfolio> existingPortfolio = portfolioRepository
                            .findByClientAndSymbol(client, symbol);
                        
                        Portfolio portfolio;
                        if (existingPortfolio.isPresent()) {
                            portfolio = existingPortfolio.get();
                            log.info("Updating existing holding: {} - Old Qty: {}, New Qty: {}", 
                                symbol, portfolio.getQuantity(), quantity.intValue());
                            portfolio.setQuantity(quantity.intValue());
                            portfolio.setAveragePrice(price);
                        } else {
                            portfolio = new Portfolio();
                            portfolio.setClient(client);
                            portfolio.setSymbol(symbol);
                            portfolio.setQuantity(quantity.intValue());
                            portfolio.setAveragePrice(price);
                            log.info("Creating new holding: {} - Qty: {}, Price: {}", symbol, quantity.intValue(), price);
                        }
                        
                        portfolioRepository.save(portfolio);
                        imported++;
                        log.debug("Successfully saved holding: {} with {} shares", symbol, quantity);
                        
                    } catch (Exception e) {
                        skipped++;
                        errors.add("Line %d: %s".formatted(processed, e.getMessage()));
                        log.error("Error parsing line {}: {}", processed, line, e);
                    }
                }
            }
            
            response.setSuccess(true);
            response.setMessage("Holdings imported successfully");
            response.setRecordsProcessed(processed);
            response.setRecordsImported(imported);
            response.setRecordsSkipped(skipped);
            response.setErrors(errors);
            
            log.info("Holdings import completed for client {}: Processed={}, Imported={}, Skipped={} (includes cash/IIAXX entries), Errors={}", 
                clientId, processed, imported, skipped, errors.size());
            
        } catch (IOException e) {
            response.setMessage("Error reading file: " + e.getMessage());
            log.error("Error importing holdings", e);
        } catch (Exception e) {
            response.setMessage("Error importing holdings: " + e.getMessage());
            log.error("Error importing holdings", e);
        }
        
        return response;
    }
    
    @Transactional
    public ImportResponse importActivity(Long clientId, String fileName) {
        ImportResponse response = new ImportResponse(false, "");
        List<String> errors = new ArrayList<>();
        int processed = 0;
        int imported = 0;
        int skipped = 0;
        
        try {
            Optional<Client> clientOpt = clientRepository.findById(clientId);
            if (!clientOpt.isPresent()) {
                response.setMessage("Client not found with ID: " + clientId);
                return response;
            }
            
            Client client = clientOpt.get();
            Account account = accountRepository.findByClientId(clientId)
                .orElseThrow(() -> new RuntimeException("Account not found for client: " + clientId));
            
            Path filePath = Paths.get(IMPORT_DIRECTORY, fileName);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
                String line;
                boolean dataSection = false;
                
                while ((line = reader.readLine()) != null) {
                    // Skip header lines
                    if (line.contains("Trade Date")) {
                        dataSection = true;
                        continue;
                    }
                    
                    if (!dataSection || line.trim().isEmpty()) {
                        continue;
                    }
                    
                    processed++;
                    
                    try {
                        String[] parts = parseCsvLine(line);
                        
                        if (parts.length < 8) {
                            skipped++;
                            log.debug("Line {}: Skipping - insufficient columns", processed);
                            continue;
                        }
                        
                        String tradeDate = parts[0].trim();
                        String description = parts[2].trim();
                        String symbolDescription = parts[4].trim();
                        String quantityStr = parts[5].trim();
                        String priceStr = parts[6].trim();
                        String amountStr = parts[7].trim();
                        
                        if (tradeDate.isEmpty() || symbolDescription.isEmpty() || quantityStr.isEmpty()) {
                            skipped++;
                            log.debug("Line {}: Skipping - missing required fields", processed);
                            continue;
                        }
                        
                        // Extract symbol (first word before space)
                        String symbol = symbolDescription.split(" ")[0].trim();
                        
                        // Handle cash - IIAXX represents cash/money market, skip it
                        if ("IIAXX".equalsIgnoreCase(symbol)) {
                            skipped++;
                            log.debug("Line {}: Skipping cash transaction (IIAXX)", processed);
                            continue;
                        }
                        
                        // Parse quantity (negative for sell)
                        BigDecimal quantity = new BigDecimal(quantityStr.replace(",", ""));
                        boolean isSell = quantity.compareTo(BigDecimal.ZERO) < 0;
                        quantity = quantity.abs();
                        
                        // Parse price if available
                        BigDecimal price = BigDecimal.ZERO;
                        if (!priceStr.isEmpty()) {
                            price = new BigDecimal(priceStr.replace("$", "").replace(",", ""));
                        } else if (!amountStr.isEmpty() && quantity.compareTo(BigDecimal.ZERO) > 0) {
                            // Calculate price from amount
                            BigDecimal amount = new BigDecimal(amountStr.replace("$", "").replace(",", "").replace("-", ""));
                            price = amount.divide(quantity, 2, BigDecimal.ROUND_HALF_UP);
                        }
                        
                        log.debug("Processing activity line {}: Date={}, Symbol={}, Type={}, Qty={}, Price={}", 
                            processed, tradeDate, symbol, isSell ? "SELL" : "BUY", quantity, price);
                        
                        // Create trade record
                        Trade trade = new Trade();
                        trade.setClientId(clientId);
                        trade.setSymbol(symbol);
                        trade.setQuantity(quantity.intValue());
                        trade.setPrice(price);
                        trade.setType(isSell ? Trade.TradeType.SELL : Trade.TradeType.BUY);
                        trade.setOrderType(Trade.OrderType.MARKET);
                        trade.setStatus(Trade.TradeStatus.EXECUTED);
                        trade.setFraudCheckPassed(true);
                        
                        // Parse and set trade time
                        try {
                            trade.setTradeTime(LocalDateTime.parse(tradeDate + " 00:00:00", 
                                DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")));
                        } catch (Exception e) {
                            log.warn("Line {}: Could not parse date '{}', using current time", processed, tradeDate);
                            trade.setTradeTime(LocalDateTime.now());
                        }
                        
                        tradeRepository.save(trade);
                        imported++;
                        log.debug("Successfully imported activity: {} {} {} shares at ${}", 
                            isSell ? "SELL" : "BUY", symbol, quantity, price);
                        
                    } catch (Exception e) {
                        skipped++;
                        errors.add("Line %d: %s".formatted(processed, e.getMessage()));
                        log.error("Error parsing activity line {}: {}", processed, line, e);
                    }
                }
            }
            
            response.setSuccess(true);
            response.setMessage("Activity imported successfully");
            response.setRecordsProcessed(processed);
            response.setRecordsImported(imported);
            response.setRecordsSkipped(skipped);
            response.setErrors(errors);
            
            log.info("Activity import completed for client {}: Processed={}, Imported={}, Skipped={}, Errors={}", 
                clientId, processed, imported, skipped, errors.size());
            
        } catch (IOException e) {
            response.setMessage("Error reading file: " + e.getMessage());
            log.error("Error importing activity", e);
        } catch (Exception e) {
            response.setMessage("Error importing activity: " + e.getMessage());
            log.error("Error importing activity", e);
        }
        
        return response;
    }
    
    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        
        return result.toArray(new String[0]);
    }
}
