package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.JobExecutionRecord;
import com.example.stockbrokerage.entity.JobExecutionRecord.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface JobExecutionRecordRepository extends JpaRepository<JobExecutionRecord, Long> {

    /** True if the job has ever been recorded (any status). */
    boolean existsByJobName(String jobName);

    /**
     * True if there is at least one record for {@code jobName} with {@code status}
     * whose {@code startedAt} is after {@code threshold}.
     * Used to detect whether a job successfully ran within a given time window.
     */
    boolean existsByJobNameAndStatusAndStartedAtAfter(String jobName, JobStatus status, LocalDateTime threshold);
}
