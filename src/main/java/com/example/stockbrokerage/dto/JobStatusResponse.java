package com.example.stockbrokerage.dto;

import java.time.LocalDateTime;

/**
 * Summary of the most-recent execution state for a scheduled job.
 *
 * @param jobName         internal job identifier (e.g. {@code "TRADE_SUGGESTION_CHECK"})
 * @param displayName     human-friendly label shown in the UI
 * @param schedule        cron / rate description for display
 * @param lastStatus      one of {@code "SUCCESS"}, {@code "FAILED"}, {@code "RUNNING"}, {@code "NEVER"}
 * @param lastRunAt       when the most-recent execution started ({@code null} if never run)
 * @param lastSuccessAt   when the job last completed successfully ({@code null} if never succeeded)
 * @param lastErrorMessage error detail from the last failure ({@code null} when no failure)
 */
public record JobStatusResponse(
        String jobName,
        String displayName,
        String schedule,
        String lastStatus,
        LocalDateTime lastRunAt,
        LocalDateTime lastSuccessAt,
        String lastErrorMessage
) {}
