package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.SwingTradePrediction;
import com.example.stockbrokerage.entity.SwingTradePrediction.SwingOutcomeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SwingTradePredictionRepository extends JpaRepository<SwingTradePrediction, Long> {

    /** All swing suggestions for a client on or after the given date, newest first. */
    List<SwingTradePrediction> findByClientIdAndSuggestedDateAfterOrderBySuggestedDateDesc(
            Long clientId, LocalDateTime after);

    /** All PENDING swing suggestions whose suggested date falls within the lookback window. */
    List<SwingTradePrediction> findByStatusAndSuggestedDateAfter(
            SwingOutcomeStatus status, LocalDateTime after);

    /** All newly resolved swing suggestions (resolved within the last lookback period). */
    List<SwingTradePrediction> findByStatusInAndResolvedDateAfter(
            List<SwingOutcomeStatus> statuses, LocalDateTime after);

    /** True if an identical suggestion (same client + symbol + day) already exists. */
    boolean existsByClientIdAndSymbolAndSuggestedDateBetween(
            Long clientId, String symbol, LocalDateTime start, LocalDateTime end);
}
