package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.Account;
import com.example.stockbrokerage.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByClient(Client client);
    Optional<Account> findByClientId(Long clientId);
}
