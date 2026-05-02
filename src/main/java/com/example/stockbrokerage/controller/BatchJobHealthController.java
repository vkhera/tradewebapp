package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.BatchJobHealthResponse;
import com.example.stockbrokerage.dto.BatchJobHealthResponse.JobHealthStatus;
import com.example.stockbrokerage.entity.JobExecutionRecord;
import com.example.stockbrokerage.entity.JobExecutionRecord.JobStatus;
import com.example.stockbrokerage.repository.JobExecutionRecordRepository;
import com.example.stockbrokerage.service.DataSyncBatchService;
import com.example.stockbrokerage.service.LimitOrderScheduler;
import com.example.stockbrokerage.service.PredictionScoringService;
import com.example.stockbrokerage.service.ReconciliationService;
import com.example.stockbrokerage.service.StockPricePredictionBatchService;
import com.example.stockbrokerage.service.SuggestedTradeTrackingService;
import com.example.stockbrokerage.service.SwingTradeTrackingService;
import com.example.stockbrokerage.service.TrendAnalysisBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Health endpoint for batch job scheduling.
 * Helps diagnose when jobs haven't run recently or are consistently failing.
 */
@RestController
@RequestMapping("/api/health/batch-jobs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health", description = "Application health and diagnostics")
public class BatchJobHealthController {

    private final JobExecutionRecordRepository jobRecordRepo;

    private static final List<BatchJobDef> BATCH_JOBS = List.of(
            new BatchJobDef(StockPricePredictionBatchService.JOB_NAME, 
                    "Hourly Price Predictions", 
                    "Every 60 minutes", 120), // allow 2 hour buffer for overruns
            new BatchJobDef(TrendAnalysisBatchService.JOB_NAME,
                    "Trend Analysis",
                    "Every 10 minutes", 20),
            new BatchJobDef(LimitOrderScheduler.JOB_NAME,
                    "Limit Order Processor",
                    "Every 5 minutes", 10),
            new BatchJobDef(ReconciliationService.JOB_NAME,
                    "Account Reconciliation",
                    "Every 1 minute", 2),
            new BatchJobDef(SuggestedTradeTrackingService.JOB_NAME,
                    "Trade Suggestion Check",
                    "Daily @ 06:00 ET", 25 * 60), // 25 hour buffer
            new BatchJobDef(SwingTradeTrackingService.JOB_NAME,
                    "Swing Trade Check",
                    "Daily @ 06:30 ET", 25 * 60),
            new BatchJobDef(DataSyncBatchService.JOB_NAME,
                    "Market Data Sync",
                    "Daily @ 02:00 ET", 25 * 60),
            new BatchJobDef(PredictionScoringService.JOB_NAME,
                    "Prediction Scoring",
                    "Weekdays @ 18:00 ET", 25 * 60)
    );

    @GetMapping
    @Operation(summary = "Get health status of all batch jobs")
    public ResponseEntity<BatchJobHealthResponse> getBatchJobHealth() {
        List<JobHealthStatus> statuses = new ArrayList<>();
        boolean allHealthy = true;

        for (BatchJobDef job : BATCH_JOBS) {
            JobExecutionRecord latest = jobRecordRepo
                                        .findTopByJobNameOrderByStartedAtDesc(job.jobName())
                    .orElse(null);

            JobHealthStatus status = new JobHealthStatus(
                    job.jobName(),
                    job.displayName(),
                    job.schedule(),
                    latest != null ? latest.getStatus().name() : "NEVER_RUN",
                    latest != null ? latest.getStartedAt() : null,
                    latest != null ? latest.getCompletedAt() : null,
                    latest != null ? latest.getErrorMessage() : null,
                    isHealthy(latest, job.maxAgeMinutes())
            );

            statuses.add(status);
            if (!status.isHealthy()) {
                allHealthy = false;
            }
        }

        return ResponseEntity.ok(new BatchJobHealthResponse(
                allHealthy ? "UP" : "DEGRADED",
                statuses,
                LocalDateTime.now()
        ));
    }

    private boolean isHealthy(JobExecutionRecord record, int maxAgeMinutes) {
        if (record == null) {
            return false; // Job has never run
        }
        
        if (record.getStatus() == JobStatus.FAILED) {
            return false; // Most recent run failed
        }

        if (record.getCompletedAt() != null) {
            LocalDateTime maxAge = LocalDateTime.now().minusMinutes(maxAgeMinutes);
            return record.getCompletedAt().isAfter(maxAge);
        }

        // Job is currently running (RUNNING status) — check if it's been stuck
        if (record.getStatus() == JobStatus.RUNNING) {
            LocalDateTime stuckThreshold = LocalDateTime.now().minusMinutes(maxAgeMinutes);
            return record.getStartedAt().isAfter(stuckThreshold);
        }

        return false;
    }

    private record BatchJobDef(String jobName, String displayName, String schedule, int maxAgeMinutes) {}
}
