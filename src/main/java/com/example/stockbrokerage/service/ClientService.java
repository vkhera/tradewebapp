package com.example.stockbrokerage.service;

import com.example.stockbrokerage.dto.ClientRequest;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Client.ClientStatus;
import com.example.stockbrokerage.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {
    
    private final ClientRepository clientRepository;
    private final AuditService auditService;
    
    @Transactional
    public Client createClient(ClientRequest request) {
        Client client = Client.builder()
            .clientCode(request.getClientCode())
            .name(request.getName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .accountBalance(request.getAccountBalance())
            .status(request.getStatus())
            .riskLevel(request.getRiskLevel())
            .dailyTradeLimit(request.getDailyTradeLimit())
            .build();
        
        Client saved = clientRepository.save(client);
        auditService.logClientEvent(saved.getId(), "CREATE", "SYSTEM", "Client created");
        log.info("Client created: {}", saved.getClientCode());
        
        return saved;
    }
    
    public Client getClientById(Long id) {
        return clientRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
    }
    
    public Client getClientByCode(String clientCode) {
        return clientRepository.findByClientCode(clientCode)
            .orElseThrow(() -> new RuntimeException("Client not found with code: " + clientCode));
    }
    
    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }
    
    public List<Client> getClientsByStatus(ClientStatus status) {
        return clientRepository.findByStatus(status);
    }
    
    @Transactional
    @CacheEvict(value = "clients", key = "#id")
    public Client updateClient(Long id, ClientRequest request) {
        Client client = getClientById(id);
        
        client.setName(request.getName());
        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());
        client.setAccountBalance(request.getAccountBalance());
        client.setStatus(request.getStatus());
        client.setRiskLevel(request.getRiskLevel());
        client.setDailyTradeLimit(request.getDailyTradeLimit());
        
        Client updated = clientRepository.save(client);
        auditService.logClientEvent(id, "UPDATE", "SYSTEM", "Client updated");
        
        return updated;
    }
    
    @Transactional
    @CacheEvict(value = "clients", key = "#id")
    public void deleteClient(Long id) {
        clientRepository.deleteById(id);
        auditService.logClientEvent(id, "DELETE", "SYSTEM", "Client deleted");
    }
}
