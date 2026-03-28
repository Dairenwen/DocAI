#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose-allin.yml"
ENV_FILE="$ROOT_DIR/.env.allin"

cd "$ROOT_DIR"
if [ -f "$ENV_FILE" ]; then
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down
else
  docker compose -f "$COMPOSE_FILE" down
fi
