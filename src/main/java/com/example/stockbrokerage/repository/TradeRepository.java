package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.Trade;
import com.example.stockbrokerage.entity.Trade.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    
    List<Trade> findByClientId(Long clientId);
    
    List<Trade> findBySymbol(String symbol);
    
    List<Trade> findByStatus(TradeStatus status);
    
    List<Trade> findByClientIdAndStatus(Long clientId, TradeStatus status);
    
    @Query("SELECT t FROM Trade t WHERE t.tradeTime >= :startTime AND t.tradeTime <= :endTime")
    List<Trade> findTradesByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    @Query("SELECT t FROM Trade t WHERE t.clientId = :clientId AND t.tradeTime >= :startTime")
    List<Trade> findTodayTradesByClient(Long clientId, LocalDateTime startTime);
    
    @Query("SELECT t FROM Trade t WHERE t.status = 'PENDING' AND t.orderType = 'LIMIT' AND t.expiryTime > :now")
    List<Trade> findActiveLimitOrders(LocalDateTime now);
}
