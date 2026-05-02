package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.Account;
import com.example.stockbrokerage.entity.Client;
import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.repository.AccountRepository;
import com.example.stockbrokerage.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FraudDetectionServiceTest {

    private TradeRepository tradeRepository;
    private ClientService clientService;
    private AccountRepository accountRepository;
    private FraudDetectionService fraudDetectionService;

    @BeforeEach
    void setUp() {
        tradeRepository = mock(TradeRepository.class);
        clientService = mock(ClientService.class);
        accountRepository = mock(AccountRepository.class);
        fraudDetectionService = new FraudDetectionService(tradeRepository, clientService, accountRepository);
    }

    @Test
    void buyOrder_usesAccountAvailableBalance_notClientAccountBalance() {
        Long clientId = 1L;
        Trade trade = buyTrade(clientId, new BigDecimal("511.52"), 10); // 5,115.20

        Client client = activeClientWithBalance(new BigDecimal("-1027993.24"));
        when(clientService.getClientById(clientId)).thenReturn(client);
        when(tradeRepository.findTodayTradesByClient(eq(clientId), any(LocalDateTime.class)))
            .thenReturn(List.of());

        Account account = new Account();
        account.setCashBalance(new BigDecimal("5615.20"));
        account.setReservedBalance(BigDecimal.ZERO);
        when(accountRepository.findByClientId(clientId)).thenReturn(Optional.of(account));

        Map<String, Object> result = fraudDetectionService.checkForFraud(trade);

        assertThat(result.get("passed")).isEqualTo(true);
        assertThat((String) result.get("reason")).doesNotContain("Insufficient account balance");
        verify(accountRepository).findByClientId(clientId);
    }

    @Test
    void buyOrder_failsWhenAccountAvailableBalanceIsInsufficient() {
        Long clientId = 1L;
        Trade trade = buyTrade(clientId, new BigDecimal("300.00"), 10); // 3,000.00

        Client client = activeClientWithBalance(new BigDecimal("1000000.00"));
        when(clientService.getClientById(clientId)).thenReturn(client);
        when(tradeRepository.findTodayTradesByClient(eq(clientId), any(LocalDateTime.class)))
            .thenReturn(List.of());

        Account account = new Account();
        account.setCashBalance(new BigDecimal("2000.00"));
        account.setReservedBalance(BigDecimal.ZERO);
        when(accountRepository.findByClientId(clientId)).thenReturn(Optional.of(account));

        Map<String, Object> result = fraudDetectionService.checkForFraud(trade);

        assertThat(result.get("passed")).isEqualTo(false);
        assertThat((String) result.get("reason")).contains("Insufficient account balance");
    }

    private Trade buyTrade(Long clientId, BigDecimal price, int quantity) {
        Trade trade = new Trade();
        trade.setClientId(clientId);
        trade.setSymbol("SMH");
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setType(Trade.TradeType.BUY);
        trade.setTradeTime(LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0));
        return trade;
    }

    private Client activeClientWithBalance(BigDecimal accountBalance) {
        Client client = new Client();
        client.setStatus(Client.ClientStatus.ACTIVE);
        client.setAccountBalance(accountBalance);
        client.setDailyTradeLimit(null);
        return client;
    }
}
