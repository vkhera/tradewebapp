package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.SuggestedTradeRecord;
import com.example.stockbrokerage.entity.SuggestedTradeRecord.TradeOutcomeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SuggestedTradeRecordRepository extends JpaRepository<SuggestedTradeRecord, Long> {

    /** All suggestions for a client on or after the given date. */
    List<SuggestedTradeRecord> findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(
            Long clientId, LocalDateTime after);

    /** All PENDING suggestions whose suggested date falls within the lookback window. */
    List<SuggestedTradeRecord> findByStatusAndSuggestedDateAfter(
            TradeOutcomeStatus status, LocalDateTime after);

    /** True if an identical suggestion (same client + symbol + date-day) already exists. */
    boolean existsByClientIdAndSymbolAndSuggestedDateBetween(
            Long clientId, String symbol, LocalDateTime start, LocalDateTime end);
}
