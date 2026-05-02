package com.example.stockbrokerage.controller;

import com.example.stockbrokerage.dto.JobStatusResponse;
import com.example.stockbrokerage.dto.PredictionDailyScoreResponse;
import com.example.stockbrokerage.dto.WeightChangesResponse;
import com.example.stockbrokerage.dto.WeightChangesResponse.SwingWeightEntry;
import com.example.stockbrokerage.dto.WeightChangesResponse.TrendWeightEntry;
import com.example.stockbrokerage.entity.JobExecutionRecord;
import com.example.stockbrokerage.entity.SwingStrategyWeight;
import com.example.stockbrokerage.repository.JobExecutionRecordRepository;
import com.example.stockbrokerage.repository.PredictionDailyScoreRepository;
import com.example.stockbrokerage.repository.SwingStrategyWeightRepository;
import com.example.stockbrokerage.repository.TrendPredictionWeightHistoryRepository;
import com.example.stockbrokerage.service.DataSyncBatchService;
import com.example.stockbrokerage.service.LimitOrderScheduler;
import com.example.stockbrokerage.service.NewsSentimentBatchService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/jobs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin – Jobs", description = "Admin: scheduled job monitoring and manual triggers")
public class JobAdminController {

    // ── Repositories ─────────────────────────────────────────────────────────
    private final JobExecutionRecordRepository     jobRecordRepo;
    private final PredictionDailyScoreRepository   scoreRepo;
    private final SwingStrategyWeightRepository    swingWeightRepo;
    private final TrendPredictionWeightHistoryRepository trendWeightRepo;

    // ── Services (for manual triggers) ───────────────────────────────────────
    private final SuggestedTradeTrackingService    tradeTrackingService;
    private final SwingTradeTrackingService          swingTrackingService;
    private final DataSyncBatchService               dataSyncService;
    private final PredictionScoringService           predictionScoringService;
    private final StockPricePredictionBatchService   predictionBatchService;
    private final TrendAnalysisBatchService          trendBatchService;
    private final LimitOrderScheduler                limitOrderScheduler;
    private final ReconciliationService              reconciliationService;
    private final NewsSentimentBatchService          newsSentimentBatchService;

    // ── Job metadata (display info only) ─────────────────────────────────────
    private static final List<JobMeta> JOB_META = List.of(
            new JobMeta(SuggestedTradeTrackingService.JOB_NAME, "Trade Suggestion Check",  "Daily @ 06:00"),
            new JobMeta(SwingTradeTrackingService.JOB_NAME,     "Swing Trade Check",        "Daily @ 06:30"),
            new JobMeta(DataSyncBatchService.JOB_NAME,          "Market Data Sync",         "Daily @ 02:00 ET"),
            new JobMeta(PredictionScoringService.JOB_NAME,      "Prediction Scoring",       "Weekdays @ 18:00"),
            new JobMeta(StockPricePredictionBatchService.JOB_NAME, "Hourly Price Predictions", "Every 60 min"),
            new JobMeta(TrendAnalysisBatchService.JOB_NAME,     "Trend Analysis",           "Every 10 min"),
            new JobMeta(LimitOrderScheduler.JOB_NAME,           "Limit Order Processor",    "Every 5 min"),
            new JobMeta(ReconciliationService.JOB_NAME,         "Account Reconciliation",   "Every 1 min"),
            new JobMeta(NewsSentimentBatchService.JOB_NAME,    "News Sentiment Analysis",  "Daily @ 02:15")
    );

    private record JobMeta(String jobName, String displayName, String schedule) {}

    // ── REST endpoints ────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all tracked jobs with their latest execution status")
    public ResponseEntity<List<JobStatusResponse>> getJobStatuses() {
        List<JobStatusResponse> result = JOB_META.stream()
                .map(meta -> {
                    List<JobExecutionRecord> history =
                            jobRecordRepo.findTop10ByJobNameOrderByStartedAtDesc(meta.jobName());

                    if (history.isEmpty()) {
                        return new JobStatusResponse(
                                meta.jobName(), meta.displayName(), meta.schedule(),
                                "NEVER", null, null, null);
                    }

                    JobExecutionRecord latest = history.get(0);
                    String status = latest.getStatus() == null ? "UNKNOWN" : latest.getStatus().name();

                    JobExecutionRecord lastSuccess = history.stream()
                            .filter(r -> r.getStatus() == JobExecutionRecord.JobStatus.SUCCESS)
                            .findFirst()
                            .orElse(null);

                    return new JobStatusResponse(
                            meta.jobName(),
                            meta.displayName(),
                            meta.schedule(),
                            status,
                            latest.getStartedAt(),
                            lastSuccess != null ? lastSuccess.getCompletedAt() : null,
                            latest.getErrorMessage()
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /** Jobs that run too long for a synchronous HTTP response — triggered in background. */
    private static final Set<String> ASYNC_JOBS = Set.of(
            DataSyncBatchService.JOB_NAME,
            StockPricePredictionBatchService.JOB_NAME,
            TrendAnalysisBatchService.JOB_NAME,
            NewsSentimentBatchService.JOB_NAME
    );

    @PostMapping("/{jobName}/trigger")
    @Operation(summary = "Manually trigger a scheduled job")
    public ResponseEntity<Map<String, String>> triggerJob(@PathVariable String jobName) {
        log.info("Admin manual trigger requested for job '{}'", jobName);

        Runnable task = switch (jobName) {
            case SuggestedTradeTrackingService.JOB_NAME  -> tradeTrackingService::checkPendingSuggestions;
            case SwingTradeTrackingService.JOB_NAME       -> swingTrackingService::evaluatePendingSwingTrades;
            case DataSyncBatchService.JOB_NAME              -> dataSyncService::syncPriceDataToDatabase;
            case PredictionScoringService.JOB_NAME         -> () -> predictionScoringService.runTracked(LocalDate.now());
            case StockPricePredictionBatchService.JOB_NAME -> predictionBatchService::runHourlyPredictionBatch;
            case TrendAnalysisBatchService.JOB_NAME        -> trendBatchService::runBatchTrendAnalysis;
            case NewsSentimentBatchService.JOB_NAME        -> newsSentimentBatchService::runDailyNewsAnalysis;
            case LimitOrderScheduler.JOB_NAME              -> limitOrderScheduler::processLimitOrders;
            case ReconciliationService.JOB_NAME            -> reconciliationService::reconcileAccounts;
            default -> null;
        };

        if (task == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unknown job: " + jobName));
        }

        if (ASYNC_JOBS.contains(jobName)) {
            // Long-running job — fire and forget; JobTracker records the outcome
            CompletableFuture.runAsync(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("Async trigger of '{}' failed: {}", jobName, e.getMessage(), e);
                }
            });
            return ResponseEntity.accepted()
                    .body(Map.of("status", "accepted", "jobName", jobName,
                                 "message", "Job started in background. Refresh in a moment."));
        }

        try {
            task.run();
            return ResponseEntity.ok(Map.of("status", "triggered", "jobName", jobName));
        } catch (Exception e) {
            log.error("Manual trigger of '{}' failed: {}", jobName, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage(), "jobName", jobName));
        }
    }

    @GetMapping("/prediction-scores")
    @Operation(summary = "Prediction daily scores (all types) for the last 90 days, grouped by type")
    public ResponseEntity<Map<String, List<PredictionDailyScoreResponse>>> getPredictionScores() {
        LocalDate cutoff = LocalDate.now().minusDays(90);
        Map<String, List<PredictionDailyScoreResponse>> grouped = scoreRepo
                .findByScoreDateAfterOrderByScoreDateAsc(cutoff)
                .stream()
                .map(s -> new PredictionDailyScoreResponse(
                        s.getId(), s.getScoreDate(),
                        s.getPredictionType() != null ? s.getPredictionType().name() : "COMBINED",
                        s.getTotalResolved(), s.getSuccessCount(), s.getFailureCount(),
                        s.getSuccessRatePct(), s.getAvgAbsoluteError(), s.getAvgPercentageError(),
                        s.getCreatedAt()))
                .collect(Collectors.groupingBy(r -> typeKey(r.predictionType())));
        return ResponseEntity.ok(grouped);
    }

    private static String typeKey(String type) {
        if (type == null) return "combined";
        return switch (type) {
            case "HOURLY_PRICE" -> "hourlyPrice";
            case "SWING_TRADE"  -> "swingTrade";
            case "TREND"        -> "trend";
            default             -> "combined";
        };
    }

    @GetMapping("/weight-changes")
    @Operation(summary = "Current swing weights and recent trend weight history")
    public ResponseEntity<WeightChangesResponse> getWeightChanges() {
        // Current swing strategy weights
        List<SwingWeightEntry> swingEntries = swingWeightRepo.findAll().stream()
                .sorted(Comparator.comparing(SwingStrategyWeight::getStrategyName))
                .map(w -> new SwingWeightEntry(
                        w.getStrategyName(), w.getWeight(),
                        w.getWinCount(), w.getLossCount(), w.getLastUpdated()))
                .collect(Collectors.toList());

        // Most recent 50 trend weight change events (all symbols combined)
        List<TrendWeightEntry> trendEntries = trendWeightRepo
                .findAll(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "changedAt")))
                .stream()
                .map(h -> new TrendWeightEntry(
                        h.getId(), h.getSymbol(), h.getTechnique(),
                        h.getPreviousWeight(), h.getNewWeight(), h.getChangedAt()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new WeightChangesResponse(swingEntries, trendEntries));
    }
}
