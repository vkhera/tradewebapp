package com.example.stockbrokerage.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily prediction accuracy summary, used by the Admin Jobs chart.
 */
public record PredictionDailyScoreResponse(
        Long id,
        LocalDate scoreDate,
        String predictionType,
        int totalResolved,
        int successCount,
        int failureCount,
        double successRatePct,
        BigDecimal avgAbsoluteError,
        BigDecimal avgPercentageError,
        LocalDateTime createdAt
) {}
