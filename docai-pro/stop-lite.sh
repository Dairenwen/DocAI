#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose-mid.yml"

stop_by_jar_keyword() {
  local keyword="$1"
  local pids
  pids=$(pgrep -f "$keyword" || true)
  if [ -n "$pids" ]; then
    echo "[STOP] $keyword -> $pids"
    kill $pids
  else
    echo "[SKIP] $keyword not running"
  fi
}

echo "Stopping backend services"
stop_by_jar_keyword "user-service/target/user-service-1.0.0.jar"
stop_by_jar_keyword "file-service/target/file-service-1.0.0-exec.jar"
stop_by_jar_keyword "ai-service/target/ai-service-1.0.0.jar"
stop_by_jar_keyword "gateway-service/target/gateway-service-1.0.0.jar"

echo "Stopping middleware containers"
cd "$ROOT_DIR/deploy"
docker compose -f "$COMPOSE_FILE" stop

echo "Stopped."
