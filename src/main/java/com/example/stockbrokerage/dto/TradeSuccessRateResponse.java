package com.example.stockbrokerage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary stats for the success rate of suggested trades.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSuccessRateResponse {

    /** Total suggestions that have been resolved (SUCCESS or FAILED). */
    private int totalResolved;

    /** Number of suggestions that hit their target. */
    private int successCount;

    /** Number of suggestions that did not hit their target within 7 days. */
    private int failedCount;

    /** Number of suggestions still awaiting outcome. */
    private int pendingCount;

    /** Success rate as a percentage (0–100), rounded to 1 decimal. NaN becomes 0. */
    private double successRatePct;
}
