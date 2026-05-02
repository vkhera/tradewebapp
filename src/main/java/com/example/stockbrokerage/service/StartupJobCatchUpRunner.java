package com.example.stockbrokerage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Runs once after the application context is fully started.
 *
 * <p>Checks the {@link JobTrackerService} job-execution history for each tracked
 * daily cron job.  If a job did not run successfully within its expected window
 * (i.e. the system was down when the cron would have fired), the job is executed
 * immediately without waiting for the next scheduled window.
 *
 * <p><b>Why a separate class?</b>  Spring's {@code @Transactional} and
 * {@code @Scheduled} proxies only apply to calls that go through the Spring proxy.
 * Having the catch-up logic in the same class as the scheduled method would bypass
 * those proxies.  By calling the public methods from outside each service bean
 * (through their Spring-managed proxy references), transactional semantics are
 * preserved.
 *
 * <p><b>Catch-up window:</b>
 * <ul>
 *   <li>{@code TRADE_SUGGESTION_CHECK} – 23 hours (daily at 06:00)</li>
 *   <li>{@code SWING_TRADE_CHECK}       – 23 hours (daily at 06:30)</li>
 *   <li>{@code DATA_SYNC}               – 25 hours (daily at 02:00 ET; extra buffer for tz drift)</li>
 *   <li>{@code DB_BACKUP}               – 25 hours (daily at 16:00 ET; runs in the pgbackup container).
 *       Because Spring Boot cannot invoke {@code pg_dump} directly, the catch-up action writes
 *       a trigger file to the shared backup directory.  The pgbackup container reads this file
 *       on its next startup and runs an immediate backup before entering its scheduled loop.
 *       {@code pg-backup.sh} also registers each run in {@code job_execution_records}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartupJobCatchUpRunner implements ApplicationListener<ApplicationReadyEvent> {

    private final JobTrackerService jobTracker;
    private final SuggestedTradeTrackingService tradeTrackingService;
    private final SwingTradeTrackingService swingTrackingService;
    private final DataSyncBatchService dataSyncService;
    private final PredictionScoringService predictionScoringService;
    private final DbBackupTriggerService dbBackupTriggerService;
    private final TrendAnalysisBatchService trendAnalysisBatchService;
    private final NewsSentimentBatchService newsSentimentBatchService;
    private final LimitOrderScheduler limitOrderScheduler;
    private final ReconciliationService reconciliationService;

    @Override
    public void onApplicationEvent(@org.springframework.lang.NonNull ApplicationReadyEvent event) {
        log.info("Startup catch-up check: evaluating missed scheduled jobs…");
        checkAndRun(SuggestedTradeTrackingService.JOB_NAME, 23,
                tradeTrackingService::checkPendingSuggestions);
        checkAndRun(SwingTradeTrackingService.JOB_NAME,     23,
                swingTrackingService::evaluatePendingSwingTrades);
        checkAndRun(DataSyncBatchService.JOB_NAME,          25,
                dataSyncService::syncPriceDataToDatabase);
        checkAndRun(TrendAnalysisBatchService.JOB_NAME,      1,
            trendAnalysisBatchService::runBatchTrendAnalysis);
        checkAndRun(NewsSentimentBatchService.JOB_NAME,      30,
            newsSentimentBatchService::runDailyNewsAnalysis);
        checkAndRun(LimitOrderScheduler.JOB_NAME,            1,
            limitOrderScheduler::processLimitOrders);
        checkAndRun(ReconciliationService.JOB_NAME,          1,
            reconciliationService::reconcileAccounts);
        // PREDICTION_SCORING: window is 25 h (fires at 18:00; extra buffer for weekends)
        checkAndRun(PredictionScoringService.JOB_NAME,      25,
                () -> predictionScoringService.runTracked(java.time.LocalDate.now().minusDays(1)));
        // DB_BACKUP: runs in the external pgbackup container (pg-backup.sh, 16:00 ET).
        // Spring Boot cannot invoke pg_dump directly; the catch-up action writes a trigger
        // file that the pgbackup container reads on its next startup.
        checkAndRun(DbBackupTriggerService.JOB_NAME,        25,
                dbBackupTriggerService::requestImmediateBackup);
    }

    /**
     * If the job was missed within {@code lookbackHours}, calls {@code job.run()}.
     * Errors during catch-up are logged but do not prevent subsequent jobs from running.
     */
    private void checkAndRun(String jobName, int lookbackHours, Runnable job) {
        try {
            if (jobTracker.wasJobMissedSinceLastRun(jobName, lookbackHours)) {
                log.info("Catch-up: '{}' missed its last scheduled run – executing now", jobName);
                job.run();
                log.info("Catch-up: '{}' completed successfully", jobName);
            } else {
                log.debug("Catch-up: '{}' ran recently – no catch-up needed", jobName);
            }
        } catch (Exception e) {
            log.error("Catch-up execution of '{}' failed: {}", jobName, e.getMessage(), e);
        }
    }
}
