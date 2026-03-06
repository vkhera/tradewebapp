package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Success-rate statistics for resolved swing trade predictions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwingTradeSuccessRateResponse {

    /** Total swing predictions that have been resolved (SUCCESS or FAILED). */
    private int totalResolved;

    /** Number of swing predictions that hit their target price in time. */
    private int successCount;

    /** Number of swing predictions that expired without hitting the target. */
    private int failedCount;

    /** Number of swing predictions still awaiting outcome. */
    private int pendingCount;

    /** Success rate as a percentage (0–100), rounded to 1 decimal. Returns 0 when no resolved records exist. */
    private double successRatePct;
}
