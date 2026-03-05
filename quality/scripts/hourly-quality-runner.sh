#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="${REPO_DIR:-/workspace/tradewebapp}"
QUALITY_DIR="$REPO_DIR/quality"
FRONTEND_DIR="$REPO_DIR/frontend"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-3600}"
RUN_IMMEDIATELY="${RUN_IMMEDIATELY:-true}"
PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://frontend}"
BASE_URL="${BASE_URL:-http://backend:8080}"
REPORTS_DIR="$QUALITY_DIR/reports/scheduled"
OBS_LOG_FILE="${OBS_LOG_FILE:-$REPO_DIR/logs/quality-scheduler.json}"

mkdir -p "$REPORTS_DIR"
mkdir -p "$(dirname "$OBS_LOG_FILE")"

cd "$REPO_DIR"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] [scheduler] $*"
}

log "Repo directory: $REPO_DIR"
log "Interval seconds: $INTERVAL_SECONDS"
log "Playwright base URL: $PLAYWRIGHT_BASE_URL"
log "API base URL: $BASE_URL"
log "Observability log file: $OBS_LOG_FILE"

log_json() {
  local level="$1"
  local message="$2"
  local ts
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  local escaped="${message//\\/\\\\}"
  escaped="${escaped//\"/\\\"}"
  printf '{"timestamp":"%s","level":"%s","service":"quality-scheduler","message":"%s"}\n' "$ts" "$level" "$escaped" >> "$OBS_LOG_FILE"
}

if [ ! -d "$QUALITY_DIR/node_modules" ]; then
  log "Installing quality dependencies..."
  npm --prefix "$QUALITY_DIR" ci
fi

if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
  log "Installing frontend dependencies..."
  npm --prefix "$FRONTEND_DIR" ci
fi

if [ "${INSTALL_PLAYWRIGHT_BROWSERS:-false}" = "true" ]; then
  log "Installing Playwright browsers..."
  npm --prefix "$QUALITY_DIR" exec -- playwright install --with-deps
fi

wait_for_targets() {
  log "Waiting for backend health..."
  until curl -fsS "$BASE_URL/actuator/health" >/dev/null; do
    sleep 5
  done

  log "Waiting for frontend..."
  until curl -fsS "$PLAYWRIGHT_BASE_URL" >/dev/null; do
    sleep 5
  done
}

run_suite_once() {
  local ts
  ts="$(date +%Y%m%d_%H%M%S)"
  local log_file="$REPORTS_DIR/hourly_suite_${ts}.log"

  log "Starting dynamic quality suite at $ts"
  log_json "INFO" "Starting dynamic quality suite at $ts"

  set +e
  local temp_output
  temp_output="$(mktemp)"
  (
    cd "$QUALITY_DIR"
    export PLAYWRIGHT_BASE_URL
    export BASE_URL
    npm run quality:docker:dynamic
  ) >"$temp_output" 2>&1
  local exit_code=$?
  set -e

  cat "$temp_output" > "$log_file"
  while IFS= read -r line; do
    log_json "INFO" "$line"
  done < "$temp_output"
  rm -f "$temp_output"

  local latest="$REPORTS_DIR/latest-status.txt"

  {
    echo "timestamp=$ts"
    echo "exit_code=$exit_code"
    echo "log_file=$(basename "$log_file")"
  } >"$latest"

  if [ "$exit_code" -eq 0 ]; then
    log "Suite passed ($ts). Log: $log_file"
    log_json "INFO" "Dynamic suite passed ($ts). Log: $log_file"
  else
    log "Suite failed ($ts). Log: $log_file"
    log_json "ERROR" "Dynamic suite failed ($ts). Log: $log_file"
  fi
}

sleep_until_next_hour() {
  local now next delay
  now=$(date +%s)
  next=$(( (now / INTERVAL_SECONDS + 1) * INTERVAL_SECONDS ))
  delay=$(( next - now ))
  log "Sleeping $delay seconds until next run..."
  sleep "$delay"
}

wait_for_targets

if [ "$RUN_IMMEDIATELY" = "true" ]; then
  run_suite_once || true
fi

while true; do
  sleep_until_next_hour
  wait_for_targets
  run_suite_once || true
done
