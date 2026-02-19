package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.ClientRequest;
import com.example.stockbrokerage.entity.AuditLog;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Client.ClientStatus;
import com.example.stockbrokerage.repository.AuditLogRepository;
import com.example.stockbrokerage.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
@Tag(name = "Admin – Clients", description = "Admin: client management and audit logs")
public class ClientAdminController {
    
    private final ClientService clientService;
    private final AuditLogRepository auditLogRepository;
    
    @PostMapping
    @Operation(summary = "Create a new client", description = "Register a new client (admin)")
    public ResponseEntity<Client> createClient(@Valid @RequestBody ClientRequest request) {
        Client client = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(client);
    }
    
    @GetMapping
    @Operation(summary = "Get all clients", description = "Retrieve all clients")
    public ResponseEntity<List<Client>> getAllClients() {
        List<Client> clients = clientService.getAllClients();
        return ResponseEntity.ok(clients);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID", description = "Retrieve client details")
    public ResponseEntity<Client> getClientById(@PathVariable Long id) {
        Client client = clientService.getClientById(id);
        return ResponseEntity.ok(client);
    }
    
    @GetMapping("/status/{status}")
    @Operation(summary = "Get clients by status", description = "Filter clients by status")
    public ResponseEntity<List<Client>> getClientsByStatus(@PathVariable ClientStatus status) {
        List<Client> clients = clientService.getClientsByStatus(status);
        return ResponseEntity.ok(clients);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update client", description = "Update client details")
    public ResponseEntity<Client> updateClient(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        Client client = clientService.updateClient(id, request);
        return ResponseEntity.ok(client);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete client", description = "Remove a client")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/audit-logs")
    @Operation(summary = "Get client audit logs", description = "Retrieve all audit logs for a client")
    public ResponseEntity<List<AuditLog>> getClientAuditLogs(@PathVariable Long id) {
        List<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityId("CLIENT", id);
        return ResponseEntity.ok(logs);
    }
}
