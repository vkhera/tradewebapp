package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Client.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    
    Optional<Client> findByClientCode(String clientCode);
    
    Optional<Client> findByEmail(String email);
    
    List<Client> findByStatus(ClientStatus status);
    
    List<Client> findByRiskLevel(String riskLevel);
}
