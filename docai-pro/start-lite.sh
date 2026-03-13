#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose-mid.yml"

mkdir -p "$LOG_DIR"

is_port_listening() {
  local port="$1"
  ss -lnt 2>/dev/null | grep -qE ":${port}\\b"
}

wait_port() {
  local port="$1"
  local name="$2"
  local timeout_sec="${3:-60}"
  local elapsed=0

  while ! is_port_listening "$port"; do
    sleep 1
    elapsed=$((elapsed + 1))
    if [ "$elapsed" -ge "$timeout_sec" ]; then
      echo "[ERROR] ${name} did not open port ${port} in ${timeout_sec}s"
      return 1
    fi
  done
  echo "[OK] ${name} is listening on ${port}"
}

start_java_service() {
  local name="$1"
  local jar_path="$2"
  local port="$3"
  local xmx="$4"

  if is_port_listening "$port"; then
    echo "[SKIP] ${name} already listening on ${port}"
    return 0
  fi

  if [ ! -f "$jar_path" ]; then
    echo "[ERROR] Missing jar: $jar_path"
    return 1
  fi

  echo "[START] ${name}"
  nohup java -Xms128m -Xmx"${xmx}" -jar "$jar_path" > "$LOG_DIR/${name}.log" 2>&1 &
  wait_port "$port" "$name" 90
}

echo "[1/3] Starting Redis/Nacos/Nginx containers"
cd "$ROOT_DIR/deploy"
docker compose -f "$COMPOSE_FILE" up -d docai-redis docai-nacos docai-web

echo "[2/3] Waiting middleware ports"
wait_port 6379 "redis" 45 || true
wait_port 8848 "nacos" 90
wait_port 8080 "nginx" 45

echo "[3/3] Starting backend services (low-memory mode)"
cd "$ROOT_DIR"
start_java_service "user-service" "$ROOT_DIR/user-service/target/user-service-1.0.0.jar" 9001 256m
start_java_service "file-service" "$ROOT_DIR/file-service/target/file-service-1.0.0-exec.jar" 9003 256m
start_java_service "ai-service" "$ROOT_DIR/ai-service/target/ai-service-1.0.0.jar" 9002 384m
start_java_service "gateway-service" "$ROOT_DIR/gateway-service/target/gateway-service-1.0.0.jar" 18080 256m

echo ""
echo "Startup complete."
echo "Frontend: http://127.0.0.1:8080"
echo "Gateway:  http://127.0.0.1:18080"
echo "Logs:     $LOG_DIR"
