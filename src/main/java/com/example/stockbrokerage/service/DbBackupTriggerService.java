package com.example.stockbrokerage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Coordinates catch-up for the external {@code pgbackup} container.
 *
 * <p>The database backup job runs as a shell script ({@code pg-backup.sh}) inside
 * a separate Docker container and is therefore invisible to Spring's scheduler.
 * When {@link StartupJobCatchUpRunner} detects that the {@code DB_BACKUP} job was
 * missed, it calls {@link #requestImmediateBackup()}, which writes a trigger file
 * to the shared {@code ./backup} host directory.
 *
 * <p>On its next startup the {@code pgbackup} container checks for this trigger
 * file and runs an immediate pg_dump before entering its normal schedule loop.
 * The pgbackup container also writes job-execution rows to
 * {@code job_execution_records} (via psql), so future catch-up checks have history
 * to compare against.
 */
@Service
@Slf4j
public class DbBackupTriggerService {

    /** Logical name used in {@code job_execution_records} by {@code pg-backup.sh}. */
    public static final String JOB_NAME = "DB_BACKUP";

    /**
     * Path to the trigger file inside the backend container.
     * Defaults to {@code /app/backup/.backup-requested} which corresponds to the
     * {@code ./backup} host directory mounted in both the backend and pgbackup containers.
     * Override via {@code backup.trigger-file} in application properties for local dev.
     */
    private final Path triggerFile;

    public DbBackupTriggerService(
            @Value("${backup.trigger-file:/app/backup/.backup-requested}") String triggerFilePath) {
        this.triggerFile = Path.of(triggerFilePath);
    }

    /**
     * Writes a trigger file that the {@code pgbackup} container reads on its next startup.
     *
     * <p>The pgbackup container will delete the file and run an immediate {@code pg_dump}
     * before entering its normal 16:00 ET schedule loop.
     *
     * <p>This method never throws: any I/O error is logged as a warning so that
     * other catch-up jobs in {@link StartupJobCatchUpRunner} are not interrupted.
     */
    public void requestImmediateBackup() {
        try {
            Files.createDirectories(triggerFile.getParent());
            Files.writeString(triggerFile, Instant.now().toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.warn("DB_BACKUP missed – trigger file written at '{}'. " +
                     "Restart the pgbackup container (docker compose restart pgbackup) " +
                     "to force an immediate catch-up backup, or wait for its next " +
                     "automatic restart.", triggerFile);
        } catch (IOException e) {
            log.error("DB_BACKUP missed but could not write trigger file '{}': {}. " +
                      "Run 'docker compose restart pgbackup' manually to force a catch-up backup.",
                      triggerFile, e.getMessage(), e);
        }
    }
}
