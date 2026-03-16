package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persisted record of a background job execution.
 *
 * <p>Every tracked scheduled job writes a row when it starts ({@code RUNNING}) and
 * updates that row on completion ({@code SUCCESS} or {@code FAILED}).  On startup,
 * the application checks this table to detect jobs that were missed while the system
 * was down and re-runs them immediately via {@link com.example.stockbrokerage.service.StartupJobCatchUpRunner}.
 */
@Entity
@Table(name = "job_execution_records",
       indexes = {
           @Index(name = "idx_jer_job_started", columnList = "job_name, started_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Logical name of the job (e.g. {@code TRADE_SUGGESTION_CHECK}). */
    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    /** When the job was supposed to run (its cron/schedule time). */
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    /** When the job actually started executing. */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** When the job finished (either success or failure). Null while RUNNING. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    /** Non-null when status is FAILED; contains the first 2 000 characters of the error. */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    public enum JobStatus {
        RUNNING, SUCCESS, FAILED
    }
}
