$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $root
$frontendRoot = Join-Path $repoRoot 'docai-frontend'
if (-not (Test-Path $frontendRoot)) {
    $frontendRoot = Join-Path $repoRoot 'frontend-service'
}
$deployDir = Join-Path $root 'deploy'
$composeFile = Join-Path $deployDir 'docker-compose-mid-windows.yml'
$logDir = Join-Path $root 'logs'
$publicHost = if ($env:PUBLIC_HOST) { $env:PUBLIC_HOST } else { 'localhost' }

New-Item -ItemType Directory -Path $logDir -Force | Out-Null

function Assert-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Command not found: $name"
    }
}

function Assert-LastExit([string]$stepName) {
    if ($LASTEXITCODE -ne 0) {
        throw "$stepName failed with exit code $LASTEXITCODE"
    }
}

function Assert-DockerEngineReady() {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Engine is not running. Please start Docker Desktop first.'
    }
}

function Get-PortOwner([int]$port) {
    $conn = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $conn) {
        return $null
    }

    $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
    if ($proc) {
        return "$($proc.Name) (PID $($proc.Id))"
    }
    return "PID $($conn.OwningProcess)"
}

function Assert-PortFree([int]$port, [string]$purpose) {
    $owner = Get-PortOwner $port
    if ($owner) {
        throw "Port $port is already in use by $owner. Please stop it before startup. Required for $purpose."
    }
}

function Get-EnvValue([string]$name, [string]$defaultValue = $null) {
    $v = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($v)) { return $v }
    $v = [Environment]::GetEnvironmentVariable($name, 'User')
    if (-not [string]::IsNullOrWhiteSpace($v)) { return $v }
    $v = [Environment]::GetEnvironmentVariable($name, 'Machine')
    if (-not [string]::IsNullOrWhiteSpace($v)) { return $v }
    return $defaultValue
}

function Test-MiddlewareAlreadyRunning() {
    $running = docker compose -f $composeFile ps --status running --services 2>$null
    if ($LASTEXITCODE -ne 0) {
        return $false
    }

    $required = @('docai-mysql', 'docai-redis', 'docai-nacos', 'docai-web')
    foreach ($svc in $required) {
        if (-not ($running -contains $svc)) {
            return $false
        }
    }
    return $true
}

function Test-PortListening([int]$port) {
    $conn = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    return $null -ne $conn
}

function Wait-Port([int]$port, [string]$name, [int]$timeoutSec = 120) {
    $elapsed = 0
    while (-not (Test-PortListening $port)) {
        Start-Sleep -Seconds 1
        $elapsed++
        if ($elapsed -ge $timeoutSec) {
            throw "$name did not listen on port $port within ${timeoutSec}s"
        }
    }
    Write-Host "[OK] $name listening on $port"
}

function Stop-JavaProcessByJar([string]$jarPath) {
    $normalized = $jarPath.Replace('/', '\\').ToLower()
    $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue
    foreach ($p in $procs) {
        if ($null -ne $p.CommandLine -and $p.CommandLine.ToLower().Contains($normalized)) {
            try {
                Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
            } catch {
                # Best effort cleanup for stale java process.
            }
        }
    }
}

function Stop-ProcessByPort([int]$port, [string]$serviceName) {
    $listeners = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if (-not $listeners) {
        return
    }

    $pids = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($processId in $pids) {
        try {
            Write-Host "[INFO] Stop stale process on port $port for $serviceName (PID $processId)"
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        } catch {
            # Best effort cleanup for stale process by port.
        }
    }
}

function Start-JavaService([string]$name, [string]$jarPath, [int]$port, [string]$xmx, [string[]]$extraArgs = @(), [string[]]$jvmArgs = @()) {
    if (Test-PortListening $port) {
        Write-Host "[SKIP] $name already running on $port"
        return
    }

    if (-not (Test-Path $jarPath)) {
        throw "Missing JAR: $jarPath"
    }

    $outLog = Join-Path $logDir "$name.log"
    $errLog = Join-Path $logDir "$name.err.log"

    Write-Host "[START] $name"
    $args = @("-Xms128m", "-Xmx$xmx") + $jvmArgs + @('-jar', $jarPath) + $extraArgs
    Start-Process -FilePath 'java' `
        -ArgumentList $args `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -WindowStyle Hidden

    Wait-Port -port $port -name $name -timeoutSec 150
}

Write-Host "[1/6] Check prerequisites"
Assert-Command 'docker'
Assert-Command 'mvn'
Assert-Command 'npm'
Assert-Command 'java'
Assert-DockerEngineReady
if (Test-MiddlewareAlreadyRunning) {
    Write-Host '[SKIP] Middleware containers already running, skip port pre-check.'
} else {
    $mysqlPreCheckPort = if ($env:MYSQL_HOST_PORT) { [int]$env:MYSQL_HOST_PORT } else { 3306 }
    Assert-PortFree -port $mysqlPreCheckPort -purpose 'mysql container'
    Assert-PortFree -port 6379 -purpose 'redis container'
    Assert-PortFree -port 8848 -purpose 'nacos container'
    Assert-PortFree -port 8080 -purpose 'nginx container'
}

Write-Host "[2/6] Build backend"
Stop-JavaProcessByJar (Join-Path $root 'user-service\target\user-service-1.0.0.jar')
Stop-JavaProcessByJar (Join-Path $root 'file-service\target\file-service-1.0.0-exec.jar')
Stop-JavaProcessByJar (Join-Path $root 'ai-service\target\ai-service-1.0.0.jar')
Stop-JavaProcessByJar (Join-Path $root 'gateway-service\target\gateway-service-1.0.0.jar')
Stop-ProcessByPort -port 9001 -serviceName 'user-service'
Stop-ProcessByPort -port 9003 -serviceName 'file-service'
Stop-ProcessByPort -port 9002 -serviceName 'ai-service'
Stop-ProcessByPort -port 18080 -serviceName 'gateway-service'
Start-Sleep -Seconds 1
Push-Location $root
mvn -DskipTests clean package
Assert-LastExit 'Backend build'
Pop-Location

Write-Host "[3/6] Build frontend"
Push-Location $frontendRoot
if (-not (Test-Path (Join-Path $frontendRoot 'node_modules'))) {
    npm install
    Assert-LastExit 'Frontend dependency install'
}
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'SilentlyContinue'
& npm run build 2>&1 | ForEach-Object { if ($_ -is [string]) { Write-Host $_ } }
$ErrorActionPreference = $prevEAP
Assert-LastExit 'Frontend build'
Pop-Location

Write-Host "[4/6] Sync frontend dist to nginx"
$targetDist = Join-Path $deployDir 'nginx\web\dist'
if (Test-Path $targetDist) {
    Remove-Item $targetDist -Recurse -Force
}
New-Item -ItemType Directory -Path $targetDist -Force | Out-Null
Copy-Item -Path (Join-Path $frontendRoot 'dist\*') -Destination $targetDist -Recurse -Force

Write-Host "[5/6] Start middleware containers"
Push-Location $deployDir
# Docker Compose writes progress/status to stderr; suppress PowerShell treating it as error
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'SilentlyContinue'
& docker compose -f $composeFile up -d 2>&1 | ForEach-Object { Write-Host $_ }
$composeExit = $LASTEXITCODE
$ErrorActionPreference = $prevEAP
if ($composeExit -ne 0) { throw "Start middleware containers failed (exit $composeExit)" }

$ErrorActionPreference = 'SilentlyContinue'
$running = & docker compose -f $composeFile ps --status running --services 2>&1
$psExit = $LASTEXITCODE
$ErrorActionPreference = $prevEAP
if ($psExit -ne 0) { throw "Verify middleware containers failed (exit $psExit)" }
# Filter to only string lines (ignore ErrorRecord objects from stderr)
$running = $running | Where-Object { $_ -is [string] }
foreach ($svc in @('docai-mysql', 'docai-redis', 'docai-nacos', 'docai-web')) {
    if (-not ($running -contains $svc)) {
        throw "Container $svc is not running. Check: docker compose -f $composeFile logs $svc"
    }
}
Pop-Location

# Detect actual MySQL host port (may differ from default 3306 if MYSQL_HOST_PORT was set or container was created with a different mapping)
$mysqlPort = 3306
$mysqlPortLines = docker port docai-mysql 3306/tcp 2>$null
if ($mysqlPortLines) {
    $firstLine = ($mysqlPortLines | Select-Object -First 1).ToString().Trim()
    if ($firstLine -match ':(\d+)$') {
        $mysqlPort = [int]$Matches[1]
    }
    Write-Host "[INFO] Detected MySQL host port: $mysqlPort"
}

Wait-Port -port $mysqlPort -name 'mysql' -timeoutSec 120
Wait-Port -port 6379 -name 'redis' -timeoutSec 120
Wait-Port -port 8848 -name 'nacos' -timeoutSec 180
Wait-Port -port 8080 -name 'nginx' -timeoutSec 120

Write-Host "[6/6] Start Java services"
$commonArgs = @(
    '--spring.cloud.bootstrap.enabled=false',
    '--spring.cloud.nacos.config.enabled=false',
    '--spring.cloud.nacos.discovery.enabled=false',
    '--security.gateway.enabled=false',
    '--spring.redis.host=127.0.0.1',
    '--spring.redis.port=6379'
)

$multipartArgs = @(
    '--spring.servlet.multipart.max-file-size=200MB',
    '--spring.servlet.multipart.max-request-size=200MB'
)

$dbArgs = @(
    "--spring.datasource.url=jdbc:mysql://127.0.0.1:${mysqlPort}/docai?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
    '--spring.datasource.username=drw',
    '--spring.datasource.password=dairenwen1092',
    '--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver'
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

$gatewayJvmArgs = @(
    '-Dserver.port=18080',
    '-Dspring.cloud.bootstrap.enabled=false',
    '-Dspring.cloud.nacos.config.enabled=false',
    '-Dspring.cloud.nacos.discovery.enabled=false',
    '-Dspring.codec.max-in-memory-size=200MB',
    '-Dspring.cloud.gateway.httpclient.response-timeout=300s',
    '-Dspring.cloud.gateway.httpclient.connect-timeout=10000'
)

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

$mailArgs = @()
$smtpHost = Get-EnvValue 'DOC_SMTP_HOST' 'smtp.qq.com'
$smtpPort = Get-EnvValue 'DOC_SMTP_PORT' '587'
$smtpUser = Get-EnvValue 'DOC_SMTP_USER'
$smtpPass = Get-EnvValue 'DOC_SMTP_AUTH_CODE'

if ($smtpUser -and $smtpPass) {
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
    Write-Host '[INFO] SMTP enabled by environment variables (DOC_SMTP_*).'
} else {
    Write-Host '[WARN] SMTP credentials not provided. Email service will run in noop mode.'
}

$ossArgs = @()
$ossEndpoint = Get-EnvValue 'DOC_OSS_ENDPOINT'
$ossBucket = Get-EnvValue 'DOC_OSS_BUCKET'
$ossAccessKeyId = Get-EnvValue 'DOC_OSS_ACCESS_KEY_ID'
$ossAccessKeySecret = Get-EnvValue 'DOC_OSS_ACCESS_KEY_SECRET'

if ($ossEndpoint -and $ossBucket -and $ossAccessKeyId -and $ossAccessKeySecret) {
    $ossArgs = @(
        "--aliyun.oss.end-point=$ossEndpoint",
        "--aliyun.oss.bucket-name=$ossBucket",
        "--aliyun.oss.access-key-id=$ossAccessKeyId",
        "--aliyun.oss.access-key-secret=$ossAccessKeySecret"
    )
    Write-Host '[INFO] Aliyun OSS enabled by environment variables (DOC_OSS_*).'
} else {
    Write-Host '[WARN] Aliyun OSS config incomplete (need DOC_OSS_ENDPOINT, DOC_OSS_BUCKET, DOC_OSS_ACCESS_KEY_ID, DOC_OSS_ACCESS_KEY_SECRET). Using local OSS fallback.'
}

Start-JavaService -name 'user-service' -jarPath (Join-Path $root 'user-service\target\user-service-1.0.0.jar') -port 9001 -xmx '256m' -extraArgs (@('--server.port=9001') + $commonArgs + $dbArgs + $mailArgs + $ossArgs)
Start-JavaService -name 'file-service' -jarPath (Join-Path $root 'file-service\target\file-service-1.0.0-exec.jar') -port 9003 -xmx '256m' -extraArgs (@('--server.port=9003') + $commonArgs + $dbArgs + $multipartArgs + $ossArgs)
Start-JavaService -name 'ai-service' -jarPath (Join-Path $root 'ai-service\target\ai-service-1.0.0.jar') -port 9002 -xmx '768m' -extraArgs (@('--server.port=9002') + $commonArgs + $dbArgs + $multipartArgs + $aiArgs + $mailArgs + $ossArgs)
Start-JavaService -name 'gateway-service' -jarPath (Join-Path $root 'gateway-service\target\gateway-service-1.0.0.jar') -port 18080 -xmx '256m' -extraArgs $gatewayArgs -jvmArgs $gatewayJvmArgs

Write-Host ''
Write-Host 'Startup complete.'
Write-Host "Frontend: http://$publicHost`:8080"
Write-Host "Gateway:  http://$publicHost`:18080"
Write-Host "Logs: $logDir"
