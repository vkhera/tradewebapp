package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.ClientRequest;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Client.ClientStatus;
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
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Client registration and profile management")
public class ClientController {
    
    private final ClientService clientService;
    
    @PostMapping
    @Operation(summary = "Create a new client", description = "Register a new client in the system")
    public ResponseEntity<Client> createClient(@Valid @RequestBody ClientRequest request) {
        Client client = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(client);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID", description = "Retrieve client details by client ID")
    public ResponseEntity<Client> getClientById(@PathVariable Long id) {
        Client client = clientService.getClientById(id);
        return ResponseEntity.ok(client);
    }
    
    @GetMapping("/code/{clientCode}")
    @Operation(summary = "Get client by code", description = "Retrieve client details by client code")
    public ResponseEntity<Client> getClientByCode(@PathVariable String clientCode) {
        Client client = clientService.getClientByCode(clientCode);
        return ResponseEntity.ok(client);
    }
    
    @GetMapping
    @Operation(summary = "Get all clients", description = "Retrieve all clients in the system")
    public ResponseEntity<List<Client>> getAllClients() {
        List<Client> clients = clientService.getAllClients();
        return ResponseEntity.ok(clients);
    }
    
    @GetMapping("/status/{status}")
    @Operation(summary = "Get clients by status", description = "Retrieve all clients with a specific status")
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
    @Operation(summary = "Delete client", description = "Remove a client from the system")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }
}
