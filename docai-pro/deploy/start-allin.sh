#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$ROOT_DIR/.env.allin"
COMPOSE_FILE="$ROOT_DIR/docker-compose-allin.yml"

if [ ! -f "$ENV_FILE" ]; then
  echo "[ERROR] Missing $ENV_FILE"
  echo "Copy .env.allin.example to .env.allin and fill DOC_DASHSCOPE_API_KEY / DOC_DEEPSEEK_API_KEY first."
  exit 1
fi

cd "$ROOT_DIR"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build

HOST_VAL="$(grep -E '^PUBLIC_HOST=' "$ENV_FILE" | cut -d '=' -f2 || true)"
if [ -z "$HOST_VAL" ]; then
  HOST_VAL="127.0.0.1"
fi

echo ""
echo "All services are starting in Docker."
echo "Frontend: http://${HOST_VAL}:8080"
