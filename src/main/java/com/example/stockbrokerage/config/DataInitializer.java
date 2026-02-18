package com.example.stockbrokerage.config;

import com.example.stockbrokerage.entity.Account;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.User;
import com.example.stockbrokerage.repository.AccountRepository;
import com.example.stockbrokerage.repository.ClientRepository;
import com.example.stockbrokerage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    
    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (userRepository.count() > 0) {
                return; // Data already initialized
            }
            
            // Create Admins
            createAdmin("admin1", "John Admin", "john.admin@stockbroker.com");
            createAdmin("admin2", "Jane Admin", "jane.admin@stockbroker.com");
            
            // Create Clients
            createClient("client1", "Alice Johnson", "alice.j@email.com", "100000.00");
            createClient("client2", "Bob Smith", "bob.smith@email.com", "50000.00");
            createClient("client3", "Charlie Brown", "charlie.b@email.com", "75000.00");
            createClient("client4", "Diana Prince", "diana.p@email.com", "150000.00");
            createClient("client5", "Eve Davis", "eve.davis@email.com", "25000.00");
        };
    }
    
    private void createAdmin(String username, String fullName, String email) {
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode("pass1234"));
        admin.setRole(User.Role.ADMIN);
        admin.setFullName(fullName);
        admin.setEmail(email);
        admin.setIsActive(true);
        userRepository.save(admin);
    }
    
    private void createClient(String username, String fullName, String email, String initialBalance) {
        // Create Client entity
        Client client = new Client();
        client.setClientCode(username.toUpperCase());
        client.setName(fullName);
        client.setEmail(email);
        client.setPhone("555-0100"); // Placeholder
        client.setAccountBalance(new BigDecimal(initialBalance));
        client.setStatus(Client.ClientStatus.ACTIVE);
        client.setRiskLevel("MEDIUM");
        client.setDailyTradeLimit(new BigDecimal("50000.00"));
        client = clientRepository.save(client);
        
        // Create User account for the client
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("pass1234"));
        user.setRole(User.Role.CLIENT);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setIsActive(true);
        user.setClient(client);
        userRepository.save(user);
        
        // Create Account with initial balance
        Account account = new Account();
        account.setClient(client);
        account.setCashBalance(new BigDecimal(initialBalance));
        account.setReservedBalance(BigDecimal.ZERO);
        accountRepository.save(account);
    }
}
