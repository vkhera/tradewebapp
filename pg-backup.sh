#!/bin/sh
# pg-backup.sh
# Runs every day at 16:00 (4 PM) America/New_York time.
# Writes a compressed pg_dump to /backup with a datestamp filename.
# Retains the last 7 daily backups to keep disk usage bounded.
set -e

export TZ="America/New_York"
BACKUP_DIR="/backup"
DB_HOST="${PGHOST:-postgres}"
DB_PORT="${PGPORT:-5432}"
DB_USER="${PGUSER:-stockuser}"
DB_NAME="${PGDATABASE:-stockdb}"
PGPASSWORD="${PGPASSWORD:-stockpass}"
export PGPASSWORD

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

mkdir -p "$BACKUP_DIR"

log "pg-backup service started. Will run pg_dump daily at 16:00 ET."

while true; do
    wait_secs=$(seconds_until_1600)
    log "Next backup in ${wait_secs}s (at 16:00 ET)."
    sleep "$wait_secs"

    STAMP=$(date '+%Y%m%d_%H%M%S')
    OUT_FILE="${BACKUP_DIR}/stockdb_${STAMP}.dump"
    TMP_FILE="${OUT_FILE}.tmp"

    log "Starting pg_dump → ${OUT_FILE} ..."
    if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -Fc "$DB_NAME" > "$TMP_FILE"; then
        mv "$TMP_FILE" "$OUT_FILE"
        log "Backup complete: ${OUT_FILE} ($(du -sh "$OUT_FILE" | cut -f1))"
    else
        log "ERROR: pg_dump failed — removing incomplete file."
        rm -f "$TMP_FILE"
    fi

    # Retain only the 7 most recent *.dump files
    ls -1t "${BACKUP_DIR}"/*.dump 2>/dev/null | tail -n +8 | xargs -r rm -f
    log "Cleanup done. Retained dumps: $(ls "${BACKUP_DIR}"/*.dump 2>/dev/null | wc -l)"

    # Sleep 23h to avoid running twice if the dump itself took < 1 s
    sleep 82800
done
