package com.example.stockbrokerage.service;

import com.example.stockbrokerage.entity.JobExecutionRecord;
import com.example.stockbrokerage.entity.JobExecutionRecord.JobStatus;
import com.example.stockbrokerage.repository.JobExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Utility service that records the lifecycle of background jobs in the database.
 *
 * <p>Tracked jobs call {@link #startJob} at the beginning and either
 * {@link #completeJob} or {@link #failJob} at the end.  The {@link #wasJobMissedSinceLastRun}
 * method is used on startup to detect jobs that should have run while the system
 * was down.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobTrackerService {

    private final JobExecutionRecordRepository repository;

    /**
     * Persists a new RUNNING record for the given job and returns it.
     * The returned record must be passed to {@link #completeJob} or {@link #failJob}.
     */
    @Transactional
    public JobExecutionRecord startJob(String jobName, LocalDateTime scheduledTime) {
        JobExecutionRecord record = JobExecutionRecord.builder()
                .jobName(jobName)
                .scheduledTime(scheduledTime)
                .startedAt(LocalDateTime.now())
                .status(JobStatus.RUNNING)
                .build();
        repository.save(record);
        log.debug("Job started: {} (id={})", jobName, record.getId());
        return record;
    }

    /** Marks the given job record as SUCCESS and sets its completion timestamp. */
    @Transactional
    public void completeJob(JobExecutionRecord record) {
        record.setStatus(JobStatus.SUCCESS);
        record.setCompletedAt(LocalDateTime.now());
        repository.save(record);
        log.debug("Job completed: {} (id={})", record.getJobName(), record.getId());
    }

    /** Marks the given job record as FAILED, storing the error message (truncated to 2 000 chars). */
    @Transactional
    public void failJob(JobExecutionRecord record, String errorMessage) {
        record.setStatus(JobStatus.FAILED);
        record.setCompletedAt(LocalDateTime.now());
        if (errorMessage != null && errorMessage.length() > 2000) {
            errorMessage = errorMessage.substring(0, 2000);
        }
        record.setErrorMessage(errorMessage);
        repository.save(record);
        log.warn("Job failed: {} (id={}) – {}", record.getJobName(), record.getId(), errorMessage);
    }

    /**
     * Returns {@code true} when the given job should be re-run on startup.
     *
     * <p>The check is conservative:
     * <ul>
     *   <li>If the job has <em>never</em> run (no DB records at all), returns {@code false}
     *       – we don't auto-trigger jobs that have never been scheduled.</li>
     *   <li>If the job has run before but has no SUCCESS within the last
     *       {@code lookbackHours} hours, returns {@code true} – the scheduled run was
     *       likely missed during downtime.</li>
     * </ul>
     *
     * @param jobName       logical job name (same value passed to {@link #startJob})
     * @param lookbackHours window to search for a recent successful run (e.g. 23 for a daily job)
     */
    public boolean wasJobMissedSinceLastRun(String jobName, int lookbackHours) {
        if (!repository.existsByJobName(jobName)) {
            // No history (e.g. new feature deployment or fresh DB) – treat as missed so any
            // pending work accumulated while tracking was absent gets processed immediately.
            log.info("No execution history for '{}' – treating as missed to process any pending work", jobName);
            return true;
        }
        LocalDateTime since = LocalDateTime.now().minusHours(lookbackHours);
        boolean hasRecentSuccess = repository.existsByJobNameAndStatusAndStartedAtAfter(
                jobName, JobStatus.SUCCESS, since);
        if (!hasRecentSuccess) {
            log.info("No successful run of '{}' found in the last {} hours – will trigger catch-up",
                    jobName, lookbackHours);
        }
        return !hasRecentSuccess;
    }
}
