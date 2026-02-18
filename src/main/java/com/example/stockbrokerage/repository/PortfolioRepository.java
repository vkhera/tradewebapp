package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.Portfolio;
import com.example.stockbrokerage.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByClient(Client client);
    List<Portfolio> findByClientId(Long clientId);
    Optional<Portfolio> findByClientAndSymbol(Client client, String symbol);
}
