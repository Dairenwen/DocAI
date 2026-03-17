# Quick service starter (skips build, assumes JARs and Docker middleware are ready)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

function Get-EnvValue([string]$name, [string]$defaultValue = $null) {
    $v = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($v)) { return $v }
    $v = [Environment]::GetEnvironmentVariable($name, 'User')
    if (-not [string]::IsNullOrWhiteSpace($v)) { return $v }
    $v = [Environment]::GetEnvironmentVariable($name, 'Machine')
    if (-not [string]::IsNullOrWhiteSpace($v)) { return $v }
    return $defaultValue
}

function Test-PortListening([int]$port) {
    $null -ne (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
}

function Stop-JavaProcessByJar([string]$jarPath) {
    $normalized = $jarPath.Replace('/', '\').ToLower()
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        if ($null -ne $_.CommandLine -and $_.CommandLine.ToLower().Contains($normalized)) {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
    }
}

# Kill existing service processes
Write-Host "[1/3] Stopping old services..."
Stop-JavaProcessByJar (Join-Path $root 'user-service\target\user-service-1.0.0.jar')
Stop-JavaProcessByJar (Join-Path $root 'file-service\target\file-service-1.0.0-exec.jar')
Stop-JavaProcessByJar (Join-Path $root 'ai-service\target\ai-service-1.0.0.jar')
Stop-JavaProcessByJar (Join-Path $root 'gateway-service\target\gateway-service-1.0.0.jar')
Start-Sleep -Seconds 2

# Detect MySQL port
Write-Host "[2/3] Detecting MySQL port..."
$mysqlPort = 3306
$mysqlPortLines = docker port docai-mysql 3306/tcp 2>$null
if ($mysqlPortLines) {
    $firstLine = ($mysqlPortLines | Select-Object -First 1).ToString().Trim()
    if ($firstLine -match ':(\d+)$') {
        $mysqlPort = [int]$Matches[1]
    }
}
Write-Host "  MySQL port: $mysqlPort"

# Build args
$commonArgs = @(
    '--spring.cloud.bootstrap.enabled=false',
    '--spring.cloud.nacos.config.enabled=false',
    '--spring.cloud.nacos.discovery.enabled=false',
    '--security.gateway.enabled=false',
    '--spring.redis.host=127.0.0.1',
    '--spring.redis.port=6379'
)
$dbArgs = @(
    "--spring.datasource.url=jdbc:mysql://127.0.0.1:${mysqlPort}/docai?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
    '--spring.datasource.username=drw',
    '--spring.datasource.password=dairenwen1092',
    '--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver'
)
$multipartArgs = @(
    '--spring.servlet.multipart.max-file-size=200MB',
    '--spring.servlet.multipart.max-request-size=200MB'
)

# SMTP
$mailArgs = @()
$smtpUser = Get-EnvValue 'DOC_SMTP_USER'
$smtpPass = Get-EnvValue 'DOC_SMTP_AUTH_CODE'
if ($smtpUser -and $smtpPass) {
    $smtpHost = Get-EnvValue 'DOC_SMTP_HOST' 'smtp.qq.com'
    $smtpPort = Get-EnvValue 'DOC_SMTP_PORT' '587'
    $mailArgs = @(
        "--spring.mail.host=$smtpHost",
        "--spring.mail.port=$smtpPort",
        "--spring.mail.username=$smtpUser",
        "--spring.mail.password=$smtpPass",
        '--spring.mail.protocol=smtp',
        '--spring.mail.default-encoding=UTF-8',
        '--spring.mail.properties.mail.smtp.auth=true',
        '--spring.mail.properties.mail.smtp.starttls.enable=true',
        '--spring.mail.properties.mail.smtp.starttls.required=false',
        '--spring.mail.properties.mail.smtp.ssl.trust=*'
    )
    Write-Host "  SMTP: enabled ($smtpUser)"
} else {
    Write-Host "  SMTP: noop mode"
}

# AI args
$dashscopeApiKey = Get-EnvValue 'DOC_DASHSCOPE_API_KEY' 'local-placeholder-key'
$deepseekApiKey = Get-EnvValue 'DOC_DEEPSEEK_API_KEY' 'local-placeholder-key'
$aiArgs = @(
    '--spring.ai.default-provider=dashscope',
    "--spring.ai.dashscope.api-key=$dashscopeApiKey",
    "--spring.ai.dashscope.chat.api-key=$dashscopeApiKey",
    '--spring.ai.dashscope.chat.options.model=qwen-plus',
    "--spring.ai.alibaba.dashscope.api-key=$dashscopeApiKey",
    '--spring.ai.alibaba.dashscope.chat.options.model=qwen-plus',
    '--spring.ai.alibaba.dashscope.chat.options.temperature=0.7',
    '--spring.ai.alibaba.dashscope.chat.options.max-tokens=8192',
    '--spring.ai.alibaba.dashscope.http.connect-timeout=60000',
    '--spring.ai.alibaba.dashscope.http.read-timeout=300000',
    '--spring.ai.alibaba.dashscope.http.write-timeout=60000',
    "--spring.ai.deepseek.api-key=$deepseekApiKey",
    '--spring.ai.deepseek.base-url=https://api.deepseek.com',
    '--spring.ai.deepseek.chat.options.model=deepseek-chat',
    '--spring.ai.deepseek.chat.options.temperature=0.7',
    '--spring.ai.deepseek.chat.options.max-tokens=8192',
    '--spring.ai.deepseek.http.connect-timeout=60000',
    '--spring.ai.deepseek.http.read-timeout=300000',
    '--spring.ai.deepseek.http.write-timeout=60000'
)

# Gateway args
$gatewayJvmArgs = @(
    '-Dserver.port=18080',
    '-Dspring.cloud.bootstrap.enabled=false',
    '-Dspring.cloud.nacos.config.enabled=false',
    '-Dspring.cloud.nacos.discovery.enabled=false',
    '-Dspring.codec.max-in-memory-size=200MB',
    '-Dspring.cloud.gateway.httpclient.response-timeout=300s',
    '-Dspring.cloud.gateway.httpclient.connect-timeout=10000'
)
$gatewayArgs = @(
    '--spring.cloud.gateway.routes[0].id=user-service',
    '--spring.cloud.gateway.routes[0].uri=http://127.0.0.1:9001',
    '--spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/users/**',
    '--spring.cloud.gateway.routes[0].filters[0]=StripPrefix=2',
    '--spring.cloud.gateway.routes[1].id=file-service',
    '--spring.cloud.gateway.routes[1].uri=http://127.0.0.1:9003',
    '--spring.cloud.gateway.routes[1].predicates[0]=Path=/api/v1/files/**',
    '--spring.cloud.gateway.routes[1].filters[0]=StripPrefix=2',
    '--spring.cloud.gateway.routes[2].id=ai-service-1',
    '--spring.cloud.gateway.routes[2].uri=http://127.0.0.1:9002',
    '--spring.cloud.gateway.routes[2].predicates[0]=Path=/api/v1/ai/**',
    '--spring.cloud.gateway.routes[2].filters[0]=StripPrefix=2',
    '--spring.cloud.gateway.routes[3].id=ai-service-2',
    '--spring.cloud.gateway.routes[3].uri=http://127.0.0.1:9002',
    '--spring.cloud.gateway.routes[3].predicates[0]=Path=/api/v1/llm/**',
    '--spring.cloud.gateway.routes[3].filters[0]=StripPrefix=2',
    '--spring.cloud.gateway.routes[4].id=ai-service-3',
    '--spring.cloud.gateway.routes[4].uri=http://127.0.0.1:9002',
    '--spring.cloud.gateway.routes[4].predicates[0]=Path=/api/v1/source/**',
    '--spring.cloud.gateway.routes[4].filters[0]=StripPrefix=2',
    '--spring.cloud.gateway.routes[5].id=ai-service-4',
    '--spring.cloud.gateway.routes[5].uri=http://127.0.0.1:9002',
    '--spring.cloud.gateway.routes[5].predicates[0]=Path=/api/v1/template/**',
    '--spring.cloud.gateway.routes[5].filters[0]=StripPrefix=2'
)

# Start services
Write-Host "[3/3] Starting services..."

Write-Host "  Starting user-service..."
Start-Process java -ArgumentList (@('-Xms128m', '-Xmx256m', '-jar', (Join-Path $root 'user-service\target\user-service-1.0.0.jar'), '--server.port=9001') + $commonArgs + $dbArgs + $mailArgs) -RedirectStandardOutput (Join-Path $logDir 'user-service.log') -RedirectStandardError (Join-Path $logDir 'user-service.err.log') -WindowStyle Hidden

Write-Host "  Starting file-service..."
Start-Process java -ArgumentList (@('-Xms128m', '-Xmx256m', '-jar', (Join-Path $root 'file-service\target\file-service-1.0.0-exec.jar'), '--server.port=9003') + $commonArgs + $dbArgs + $multipartArgs) -RedirectStandardOutput (Join-Path $logDir 'file-service.log') -RedirectStandardError (Join-Path $logDir 'file-service.err.log') -WindowStyle Hidden

Write-Host "  Starting ai-service..."
Start-Process java -ArgumentList (@('-Xms256m', '-Xmx768m', '-jar', (Join-Path $root 'ai-service\target\ai-service-1.0.0.jar'), '--server.port=9002') + $commonArgs + $dbArgs + $multipartArgs + $aiArgs + $mailArgs) -RedirectStandardOutput (Join-Path $logDir 'ai-service.log') -RedirectStandardError (Join-Path $logDir 'ai-service.err.log') -WindowStyle Hidden

Write-Host "  Starting gateway-service..."
Start-Process java -ArgumentList ($gatewayJvmArgs + @('-jar', (Join-Path $root 'gateway-service\target\gateway-service-1.0.0.jar')) + $gatewayArgs) -RedirectStandardOutput (Join-Path $logDir 'gateway-service.log') -RedirectStandardError (Join-Path $logDir 'gateway-service.err.log') -WindowStyle Hidden

# Wait for services
Write-Host "  Waiting for services to start (up to 150s)..."
$services = @(
    @{Name='user-service'; Port=9001},
    @{Name='file-service'; Port=9003},
    @{Name='ai-service'; Port=9002},
    @{Name='gateway-service'; Port=18080}
)

$timeout = 150
$elapsed = 0
$allUp = $false
while ($elapsed -lt $timeout -and -not $allUp) {
    Start-Sleep -Seconds 5
    $elapsed += 5
    $allUp = $true
    foreach ($svc in $services) {
        if (-not (Test-PortListening $svc.Port)) {
            $allUp = $false
            break
        }
    }
}

Write-Host ""
foreach ($svc in $services) {
    $status = if (Test-PortListening $svc.Port) { 'OK' } else { 'FAIL' }
    Write-Host "  [$status] $($svc.Name) :$($svc.Port)"
}

if ($allUp) {
    Write-Host ""
    Write-Host "All services started successfully!"
    Write-Host "Frontend: http://localhost:8080"
    Write-Host "Gateway:  http://localhost:18080"
} else {
    Write-Host ""
    Write-Host "Some services failed to start. Check logs in: $logDir"
    exit 1
}
