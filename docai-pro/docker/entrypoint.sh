#!/bin/sh
set -eu

if [ -z "${APP_JAR:-}" ]; then
  echo "[ERROR] APP_JAR is required"
  exit 1
fi

exec sh -c "java ${JAVA_OPTS:-} -jar ${APP_JAR} ${APP_ARGS:-}"
