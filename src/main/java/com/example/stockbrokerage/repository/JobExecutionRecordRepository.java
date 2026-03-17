package com.example.stockbrokerage.repository;

import com.example.stockbrokerage.entity.JobExecutionRecord;
import com.example.stockbrokerage.entity.JobExecutionRecord.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobExecutionRecordRepository extends JpaRepository<JobExecutionRecord, Long> {

    /** True if the job has ever been recorded (any status). */
    boolean existsByJobName(String jobName);

    /**
     * True if there is at least one record for {@code jobName} with {@code status}
     * whose {@code startedAt} is after {@code threshold}.
     */
    boolean existsByJobNameAndStatusAndStartedAtAfter(String jobName, JobStatus status, LocalDateTime threshold);

    /** The most recent execution record for the given job name. */
    Optional<JobExecutionRecord> findTopByJobNameOrderByStartedAtDesc(String jobName);

    /** The most recent N executions for a job (for history display). */
    List<JobExecutionRecord> findTop10ByJobNameOrderByStartedAtDesc(String jobName);
}
