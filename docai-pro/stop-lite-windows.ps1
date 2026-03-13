$ErrorActionPreference = 'Continue'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$deployDir = Join-Path $root 'deploy'
$composeFile = Join-Path $deployDir 'docker-compose-mid-windows.yml'

function Stop-ByJarKeyword([string]$keyword) {
    $procs = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like "*$keyword*" }
    if ($procs) {
        $ids = $procs | ForEach-Object { $_.ProcessId }
        Write-Host "[STOP] $keyword -> $($ids -join ', ')"
        $ids | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
    }
    else {
        Write-Host "[SKIP] $keyword not running"
    }
}

Write-Host 'Stopping Java services'
Stop-ByJarKeyword 'user-service\\target\\user-service-1.0.0.jar'
Stop-ByJarKeyword 'file-service\\target\\file-service-1.0.0-exec.jar'
Stop-ByJarKeyword 'ai-service\\target\\ai-service-1.0.0.jar'
Stop-ByJarKeyword 'gateway-service\\target\\gateway-service-1.0.0.jar'

Write-Host 'Stopping Docker middleware'
Push-Location $deployDir
docker compose -f $composeFile down
Pop-Location

Write-Host 'Stopped'
