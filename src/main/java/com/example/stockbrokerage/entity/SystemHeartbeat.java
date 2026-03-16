package com.example.stockbrokerage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single-row table that records the application's last heartbeat timestamp.
 *
 * <p>A scheduler writes to this row every minute.  By comparing
 * {@link #lastHeartbeat} against the current time one can determine whether a
 * gap in job execution records was caused by:
 * <ul>
 *   <li><b>System downtime</b> – heartbeat is stale, meaning the JVM was not
 *       running.</li>
 *   <li><b>Job failure</b> – heartbeat is recent but the job status is FAILED
 *       or absent, meaning the JVM was alive but the job threw an exception.</li>
 * </ul>
 *
 * <p>The row is always upserted with {@code id = 1}.
 */
@Entity
@Table(name = "system_heartbeat")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemHeartbeat {

    /** Always {@code 1} – this is a single-row table. */
    @Id
    private Long id;

    /** Timestamp of the most recent heartbeat write. */
    @Column(name = "last_heartbeat", nullable = false)
    private LocalDateTime lastHeartbeat;

    /** When the current JVM instance started. Updated on every startup. */
    @Column(name = "startup_time")
    private LocalDateTime startupTime;
}
