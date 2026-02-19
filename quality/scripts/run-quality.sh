#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:-all}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QUALITY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$QUALITY_DIR"

step() {
  local name="$1"
  local cmd="$2"
  echo ""
  echo "=== ${name} ==="
  eval "$cmd"
}

if [ ! -d "$QUALITY_DIR/node_modules" ]; then
  echo "Installing quality tool dependencies..."
  npm install
fi

case "$TARGET" in
  all)
    step "Backend Quality" "npm run quality:backend"
    step "Frontend Quality" "npm run quality:frontend"
    step "Static Security" "npm run security:static"
    step "Dependency Security" "npm run security:deps"
    step "Frontend Functional (Post Build)" "npm run frontend:test:postbuild"
    step "API Functional" "npm run api:functional"
    step "API Performance (Smoke)" "npm run api:perf:smoke"
    ;;
  backend)
    step "Backend Quality" "npm run quality:backend"
    ;;
  frontend)
    step "Frontend Quality" "npm run quality:frontend"
    ;;
  security-static)
    step "Static Security" "npm run security:static"
    ;;
  security-deps)
    step "Dependency Security" "npm run security:deps"
    ;;
  frontend-functional)
    step "Frontend Functional (Post Build)" "npm run frontend:test:postbuild"
    ;;
  frontend-perf)
    step "Frontend Performance" "npm run frontend:perf"
    ;;
  api-functional)
    step "API Functional" "npm run api:functional"
    ;;
  api-perf-smoke)
    step "API Performance Smoke" "npm run api:perf:smoke"
    ;;
  api-perf-load)
    step "API Performance Load" "npm run api:perf:load"
    ;;
  *)
    echo "Unknown target: $TARGET"
    echo "Allowed targets: all backend frontend security-static security-deps frontend-functional frontend-perf api-functional api-perf-smoke api-perf-load"
    exit 1
    ;;
esac

echo ""
echo "Quality pipeline completed for target: $TARGET"
