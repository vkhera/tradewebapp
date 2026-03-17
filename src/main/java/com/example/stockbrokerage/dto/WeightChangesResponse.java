package com.example.stockbrokerage.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated weight-change data returned by the admin jobs endpoint.
 */
public record WeightChangesResponse(
        List<SwingWeightEntry> swingWeights,
        List<TrendWeightEntry> recentTrendChanges
) {

    /** Current adaptive weight for a swing-trade strategy. */
    public record SwingWeightEntry(
            String strategyName,
            double weight,
            int winCount,
            int lossCount,
            LocalDateTime lastUpdated
    ) {}

    /** One historical trend-prediction weight change event. */
    public record TrendWeightEntry(
            Long id,
            String symbol,
            String technique,
            Double previousWeight,
            Double newWeight,
            LocalDateTime changedAt
    ) {}
}
