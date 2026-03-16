#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(dirname "$ROOT_DIR")"
FRONTEND_ROOT="$REPO_ROOT/docai-frontend"
if [ ! -d "$FRONTEND_ROOT" ]; then
    FRONTEND_ROOT="$REPO_ROOT/frontend-service"
fi
DEPLOY_DIR="$ROOT_DIR/deploy"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose-mid.yml"
LOG_DIR="$ROOT_DIR/logs"
PUBLIC_HOST="${PUBLIC_HOST:-0.0.0.0}"

mkdir -p "$LOG_DIR"

# ---------- helper functions ----------

assert_command() {
    if ! command -v "$1" &>/dev/null; then
        echo "[ERROR] Command not found: $1"
        exit 1
    fi
}

is_port_listening() {
    local port="$1"
    ss -lnt 2>/dev/null | grep -qE ":${port}\b" || netstat -lnt 2>/dev/null | grep -qE ":${port}\b"
}

wait_port() {
    local port="$1"
    local name="$2"
    local timeout_sec="${3:-120}"
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

stop_java_by_jar() {
    local keyword="$1"
    local pids
    pids=$(pgrep -f "$keyword" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo "[STOP] Killing existing $keyword processes: $pids"
        kill $pids 2>/dev/null || true
        sleep 1
    fi
}

start_java_service() {
    local name="$1"
    local jar_path="$2"
    local port="$3"
    local xmx="$4"
    shift 4
    local extra_args=("$@")

    if is_port_listening "$port"; then
        echo "[SKIP] ${name} already listening on ${port}"
        return 0
    fi

    if [ ! -f "$jar_path" ]; then
        echo "[ERROR] Missing jar: $jar_path"
        return 1
    fi

    echo "[START] ${name}"
    nohup java -Xms128m -Xmx"${xmx}" -jar "$jar_path" "${extra_args[@]}" \
        > "$LOG_DIR/${name}.log" 2>&1 &
    wait_port "$port" "$name" 150
}

get_env_value() {
    local name="$1"
    local default_val="${2:-}"
    local val="${!name:-}"
    if [ -n "$val" ]; then
        echo "$val"
    else
        echo "$default_val"
    fi
}

# ---------- [1/6] Prerequisites ----------
echo "[1/6] Check prerequisites"
assert_command docker
assert_command mvn
assert_command npm
assert_command java

# ---------- [2/6] Build backend ----------
echo "[2/6] Build backend"
stop_java_by_jar "user-service-1.0.0.jar"
stop_java_by_jar "file-service-1.0.0-exec.jar"
stop_java_by_jar "ai-service-1.0.0.jar"
stop_java_by_jar "gateway-service-1.0.0.jar"
sleep 1

cd "$ROOT_DIR"
mvn -DskipTests clean package
echo "[OK] Backend build complete"

# ---------- [3/6] Build frontend ----------
echo "[3/6] Build frontend"
cd "$FRONTEND_ROOT"
if [ ! -d "node_modules" ]; then
    npm install
fi
npm run build
echo "[OK] Frontend build complete"

# ---------- [4/6] Sync frontend dist ----------
echo "[4/6] Sync frontend dist to nginx"
TARGET_DIST="$DEPLOY_DIR/nginx/web/dist"
rm -rf "$TARGET_DIST"
mkdir -p "$TARGET_DIST"
cp -r "$FRONTEND_ROOT/dist/"* "$TARGET_DIST/"

# ---------- [5/6] Start middleware ----------
echo "[5/6] Start middleware containers"
cd "$DEPLOY_DIR"
docker compose -p docai -f "$COMPOSE_FILE" up -d
echo "[OK] Middleware containers started"

# Detect actual MySQL host port
MYSQL_PORT=3306
MYSQL_PORT_OUTPUT=$(docker port docai-mysql 3306/tcp 2>/dev/null | head -1)
if [[ "$MYSQL_PORT_OUTPUT" =~ :([0-9]+)$ ]]; then
    MYSQL_PORT="${BASH_REMATCH[1]}"
    echo "[INFO] Detected MySQL host port: $MYSQL_PORT"
fi

wait_port "$MYSQL_PORT" "mysql" 120 || true
wait_port 6379 "redis" 60 || true
wait_port 8848 "nacos" 180
wait_port 8080 "nginx" 60

# ---------- [6/6] Start Java services ----------
echo "[6/6] Start Java services"
cd "$ROOT_DIR"

# Common Spring Boot args (Nacos/bootstrap disabled for lite mode)
COMMON_ARGS=(
    "--spring.cloud.bootstrap.enabled=false"
    "--spring.cloud.nacos.config.enabled=false"
    "--spring.cloud.nacos.discovery.enabled=false"
    "--security.gateway.enabled=false"
    "--spring.redis.host=127.0.0.1"
    "--spring.redis.port=6379"
)

MULTIPART_ARGS=(
    "--spring.servlet.multipart.max-file-size=200MB"
    "--spring.servlet.multipart.max-request-size=200MB"
)

DB_ARGS=(
    "--spring.datasource.url=jdbc:mysql://127.0.0.1:${MYSQL_PORT}/docai?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
    "--spring.datasource.username=drw"
    "--spring.datasource.password=dairenwen1092"
    "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
)

GATEWAY_ARGS=(
    "--server.port=18080"
    "--spring.cloud.bootstrap.enabled=false"
    "--spring.cloud.nacos.config.enabled=false"
    "--spring.cloud.nacos.discovery.enabled=false"
    "--spring.codec.max-in-memory-size=200MB"
    "--spring.cloud.gateway.httpclient.response-timeout=300s"
    "--spring.cloud.gateway.httpclient.connect-timeout=10000"
    "--spring.cloud.gateway.routes[0].id=user-service"
    "--spring.cloud.gateway.routes[0].uri=http://127.0.0.1:9001"
    "--spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/users/**"
    "--spring.cloud.gateway.routes[0].filters[0]=StripPrefix=2"
    "--spring.cloud.gateway.routes[1].id=file-service"
    "--spring.cloud.gateway.routes[1].uri=http://127.0.0.1:9003"
    "--spring.cloud.gateway.routes[1].predicates[0]=Path=/api/v1/files/**"
    "--spring.cloud.gateway.routes[1].filters[0]=StripPrefix=2"
    "--spring.cloud.gateway.routes[2].id=ai-service-1"
    "--spring.cloud.gateway.routes[2].uri=http://127.0.0.1:9002"
    "--spring.cloud.gateway.routes[2].predicates[0]=Path=/api/v1/ai/**"
    "--spring.cloud.gateway.routes[2].filters[0]=StripPrefix=2"
    "--spring.cloud.gateway.routes[3].id=ai-service-2"
    "--spring.cloud.gateway.routes[3].uri=http://127.0.0.1:9002"
    "--spring.cloud.gateway.routes[3].predicates[0]=Path=/api/v1/llm/**"
    "--spring.cloud.gateway.routes[3].filters[0]=StripPrefix=2"
    "--spring.cloud.gateway.routes[4].id=ai-service-3"
    "--spring.cloud.gateway.routes[4].uri=http://127.0.0.1:9002"
    "--spring.cloud.gateway.routes[4].predicates[0]=Path=/api/v1/source/**"
    "--spring.cloud.gateway.routes[4].filters[0]=StripPrefix=2"
    "--spring.cloud.gateway.routes[5].id=ai-service-4"
    "--spring.cloud.gateway.routes[5].uri=http://127.0.0.1:9002"
    "--spring.cloud.gateway.routes[5].predicates[0]=Path=/api/v1/template/**"
    "--spring.cloud.gateway.routes[5].filters[0]=StripPrefix=2"
)

# AI Model Configuration
DASHSCOPE_API_KEY=$(get_env_value "DOC_DASHSCOPE_API_KEY" "local-placeholder-key")
DEEPSEEK_API_KEY=$(get_env_value "DOC_DEEPSEEK_API_KEY" "local-placeholder-key")

AI_ARGS=(
    "--spring.ai.default-provider=dashscope"
    "--spring.ai.dashscope.api-key=$DASHSCOPE_API_KEY"
    "--spring.ai.dashscope.chat.api-key=$DASHSCOPE_API_KEY"
    "--spring.ai.dashscope.chat.options.model=qwen-plus"
    "--spring.ai.alibaba.dashscope.api-key=$DASHSCOPE_API_KEY"
    "--spring.ai.alibaba.dashscope.chat.options.model=qwen-plus"
    "--spring.ai.alibaba.dashscope.chat.options.temperature=0.7"
    "--spring.ai.alibaba.dashscope.chat.options.max-tokens=8192"
    "--spring.ai.alibaba.dashscope.http.connect-timeout=60000"
    "--spring.ai.alibaba.dashscope.http.read-timeout=300000"
    "--spring.ai.alibaba.dashscope.http.write-timeout=60000"
    "--spring.ai.deepseek.api-key=$DEEPSEEK_API_KEY"
    "--spring.ai.deepseek.base-url=https://api.deepseek.com"
    "--spring.ai.deepseek.chat.options.model=deepseek-chat"
    "--spring.ai.deepseek.chat.options.temperature=0.7"
    "--spring.ai.deepseek.chat.options.max-tokens=8192"
    "--spring.ai.deepseek.http.connect-timeout=60000"
    "--spring.ai.deepseek.http.read-timeout=300000"
    "--spring.ai.deepseek.http.write-timeout=60000"
)

# SMTP Configuration
MAIL_ARGS=()
SMTP_HOST=$(get_env_value "DOC_SMTP_HOST" "smtp.qq.com")
SMTP_PORT=$(get_env_value "DOC_SMTP_PORT" "587")
SMTP_USER=$(get_env_value "DOC_SMTP_USER" "")
SMTP_PASS=$(get_env_value "DOC_SMTP_AUTH_CODE" "")

if [ -n "$SMTP_USER" ] && [ -n "$SMTP_PASS" ]; then
    MAIL_ARGS=(
        "--spring.mail.host=$SMTP_HOST"
        "--spring.mail.port=$SMTP_PORT"
        "--spring.mail.username=$SMTP_USER"
        "--spring.mail.password=$SMTP_PASS"
        "--spring.mail.protocol=smtp"
        "--spring.mail.default-encoding=UTF-8"
        "--spring.mail.properties.mail.smtp.auth=true"
        "--spring.mail.properties.mail.smtp.starttls.enable=true"
        "--spring.mail.properties.mail.smtp.starttls.required=false"
        "--spring.mail.properties.mail.smtp.ssl.trust=*"
    )
    echo "[INFO] SMTP enabled."
else
    echo "[WARN] SMTP credentials not provided. Email service in noop mode."
fi

# OSS Configuration
OSS_ARGS=()
OSS_ENDPOINT=$(get_env_value "DOC_OSS_ENDPOINT" "")
OSS_BUCKET=$(get_env_value "DOC_OSS_BUCKET" "")
OSS_ACCESS_KEY_ID=$(get_env_value "DOC_OSS_ACCESS_KEY_ID" "")
OSS_ACCESS_KEY_SECRET=$(get_env_value "DOC_OSS_ACCESS_KEY_SECRET" "")

if [ -n "$OSS_ENDPOINT" ] && [ -n "$OSS_BUCKET" ] && [ -n "$OSS_ACCESS_KEY_ID" ] && [ -n "$OSS_ACCESS_KEY_SECRET" ]; then
    OSS_ARGS=(
        "--aliyun.oss.end-point=$OSS_ENDPOINT"
        "--aliyun.oss.bucket-name=$OSS_BUCKET"
        "--aliyun.oss.access-key-id=$OSS_ACCESS_KEY_ID"
        "--aliyun.oss.access-key-secret=$OSS_ACCESS_KEY_SECRET"
    )
    echo "[INFO] Aliyun OSS enabled."
else
    echo "[WARN] OSS config incomplete. Using local OSS fallback."
fi

start_java_service "user-service" \
    "$ROOT_DIR/user-service/target/user-service-1.0.0.jar" 9001 256m \
    "--server.port=9001" "${COMMON_ARGS[@]}" "${DB_ARGS[@]}" "${MAIL_ARGS[@]}" "${OSS_ARGS[@]}"

start_java_service "file-service" \
    "$ROOT_DIR/file-service/target/file-service-1.0.0-exec.jar" 9003 256m \
    "--server.port=9003" "${COMMON_ARGS[@]}" "${DB_ARGS[@]}" "${MULTIPART_ARGS[@]}" "${OSS_ARGS[@]}"

start_java_service "ai-service" \
    "$ROOT_DIR/ai-service/target/ai-service-1.0.0.jar" 9002 384m \
    "--server.port=9002" "${COMMON_ARGS[@]}" "${DB_ARGS[@]}" "${MULTIPART_ARGS[@]}" "${AI_ARGS[@]}" "${MAIL_ARGS[@]}" "${OSS_ARGS[@]}"

start_java_service "gateway-service" \
    "$ROOT_DIR/gateway-service/target/gateway-service-1.0.0.jar" 18080 256m \
    "${GATEWAY_ARGS[@]}"

echo ""
echo "========================================"
echo "  Startup complete!"
echo "========================================"
echo "Frontend: http://${PUBLIC_HOST}:8080"
echo "Gateway:  http://${PUBLIC_HOST}:18080"
echo "Logs:     $LOG_DIR"
