#!/bin/sh
# pg-backup.sh
# Runs every day at 16:00 (4 PM) America/New_York time.
# Writes a compressed pg_dump to /backup with a datestamp filename.
# Retains the last 7 daily backups to keep disk usage bounded.
#
# Catch-up on startup: if no backup exists from the last 25 hours (or a trigger
# file was written by the Spring Boot backend), an immediate backup is taken
# before entering the normal scheduled loop.  This ensures missed backups are
# recovered automatically whenever the container restarts.
#
# Job tracking: each run writes a row to the job_execution_records table
# (RUNNING → SUCCESS/FAILED) so the Spring Boot StartupJobCatchUpRunner can
# detect gaps and request a catch-up backup via the trigger file mechanism.
set -e

export TZ="America/New_York"
BACKUP_DIR="/backup"
DB_HOST="${PGHOST:-postgres}"
DB_PORT="${PGPORT:-5432}"
DB_USER="${PGUSER:-stockuser}"
DB_NAME="${PGDATABASE:-stockdb}"
PGPASSWORD="${PGPASSWORD:-stockpass}"
export PGPASSWORD

# Trigger file written by the Spring Boot backend when it detects this job was
# missed. The shared ./backup host directory is mounted in both containers.
TRIGGER_FILE="${BACKUP_DIR}/.backup-requested"

# 25 hours in seconds — catch-up threshold matching the Spring Boot lookback window
CATCHUP_THRESHOLD=90000

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S %Z')] $*"; }

seconds_until_1600() {
    now_h=$(date +%-H)
    now_m=$(date +%-M)
    now_s=$(date +%-S)
    current_secs=$(( now_h * 3600 + now_m * 60 + now_s ))
    target_secs=$(( 16 * 3600 ))   # 16:00:00

    if [ "$current_secs" -lt "$target_secs" ]; then
        echo $(( target_secs - current_secs ))
    else
        # Already past 4 PM today — wait until 4 PM tomorrow
        echo $(( 86400 - current_secs + target_secs ))
    fi
}

# ── Job tracker helpers ────────────────────────────────────────────────────────
# Best-effort: psql failures are logged but never abort the backup itself.

psql_exec() {
    # Usage: psql_exec "SQL statement"
    set +e
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -At \
         -c "$1" 2>/dev/null
    set -e
}

record_job_start() {
    set +e
    JOB_ID=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -At \
        -c "INSERT INTO job_execution_records (job_name, scheduled_time, started_at, status) \
            VALUES ('DB_BACKUP', NOW(), NOW(), 'RUNNING') RETURNING id;" 2>/dev/null)
    set -e
    log "Job tracker: DB_BACKUP started (id=${JOB_ID:-unknown})"
    echo "$JOB_ID"
}

record_job_success() {
    _id="$1"
    [ -z "$_id" ] && return
    psql_exec "UPDATE job_execution_records \
               SET status='SUCCESS', completed_at=NOW() \
               WHERE id=${_id};"
    log "Job tracker: DB_BACKUP id=${_id} → SUCCESS"
}

record_job_failure() {
    _id="$1"
    _msg=$(echo "$2" | cut -c1-2000 | sed "s/'/''/g")
    [ -z "$_id" ] && return
    psql_exec "UPDATE job_execution_records \
               SET status='FAILED', completed_at=NOW(), error_message='${_msg}' \
               WHERE id=${_id};"
    log "Job tracker: DB_BACKUP id=${_id} → FAILED"
}

# ── Core backup function ───────────────────────────────────────────────────────
run_backup() {
    STAMP=$(date '+%Y%m%d_%H%M%S')
    OUT_FILE="${BACKUP_DIR}/stockdb_${STAMP}.dump"
    TMP_FILE="${OUT_FILE}.tmp"

    JOB_ID=$(record_job_start)

    log "Starting pg_dump → ${OUT_FILE} ..."
    if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -Fc "$DB_NAME" > "$TMP_FILE"; then
        mv "$TMP_FILE" "$OUT_FILE"
        log "Backup complete: ${OUT_FILE} ($(du -sh "$OUT_FILE" | cut -f1))"
        record_job_success "$JOB_ID"
    else
        log "ERROR: pg_dump failed — removing incomplete file."
        rm -f "$TMP_FILE"
        record_job_failure "$JOB_ID" "pg_dump exited with non-zero status"
    fi

    # Retain only the 7 most recent *.dump files
    ls -1t "${BACKUP_DIR}"/*.dump 2>/dev/null | tail -n +8 | xargs -r rm -f
    log "Cleanup done. Retained dumps: $(ls "${BACKUP_DIR}"/*.dump 2>/dev/null | wc -l)"
}

# ── Startup ────────────────────────────────────────────────────────────────────
mkdir -p "$BACKUP_DIR"
log "pg-backup service started. Will run pg_dump daily at 16:00 ET."

# Check 1: trigger file written by the Spring Boot backend catch-up runner.
if [ -f "$TRIGGER_FILE" ]; then
    log "Catch-up trigger file found (written by Spring Boot backend) – running immediate backup."
    rm -f "$TRIGGER_FILE"
    run_backup
else
    # Check 2: most recent dump is older than the 25-hour catch-up threshold.
    LATEST_DUMP=$(ls -1t "${BACKUP_DIR}"/*.dump 2>/dev/null | head -1)
    if [ -z "$LATEST_DUMP" ]; then
        log "No backup dumps found on startup – running immediate catch-up backup."
        run_backup
    else
        LATEST_MTIME=$(stat -c %Y "$LATEST_DUMP")
        NOW_EPOCH=$(date +%s)
        AGE_SECS=$(( NOW_EPOCH - LATEST_MTIME ))
        if [ "$AGE_SECS" -gt "$CATCHUP_THRESHOLD" ]; then
            log "Most recent backup is ${AGE_SECS}s old (>${CATCHUP_THRESHOLD}s / 25h) – running immediate catch-up backup."
            run_backup
        else
            log "Most recent backup ${LATEST_DUMP} is ${AGE_SECS}s old – within 25h window, no catch-up needed."
        fi
    fi
fi

# ── Main daily schedule loop ───────────────────────────────────────────────────
while true; do
    wait_secs=$(seconds_until_1600)
    log "Next backup in ${wait_secs}s (at 16:00 ET)."
    sleep "$wait_secs"

    run_backup

    # Sleep 23h to avoid running twice if the dump itself took < 1 s
    sleep 82800
done
