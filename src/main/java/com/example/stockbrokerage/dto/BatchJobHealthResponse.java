package com.example.stockbrokerage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchJobHealthResponse {
    private String status; // "UP" or "DEGRADED"
    private List<JobHealthStatus> jobs;
    private LocalDateTime checkedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JobHealthStatus {
        private String jobName;
        private String displayName;
        private String schedule;
        private String lastStatus;
        private LocalDateTime lastStarted;
        private LocalDateTime lastCompleted;
        private String lastError;

        @JsonProperty("isHealthy")
        private boolean healthy;
    }
}
