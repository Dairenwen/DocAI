$ErrorActionPreference = 'Stop'

$gatewayBase = 'http://localhost:18080/api/v1'
$userBase = 'http://localhost:9001'
$fileBase = 'http://localhost:9003'
$aiBase = 'http://localhost:9002'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$sourceFile = Join-Path $workspaceRoot 'test-data\source.md'
$templateFile = Join-Path $workspaceRoot 'test-data\template.xlsx'
$outDownload = Join-Path $workspaceRoot 'test-data\template-filled-result.xlsx'
$outJson = Join-Path $PSScriptRoot 'logs\smoke-result.json'

if (-not (Test-Path $sourceFile)) {
    throw "Missing test source file: $sourceFile"
}
if (-not (Test-Path $templateFile)) {
    throw "Missing test template file: $templateFile"
}

$results = New-Object System.Collections.Generic.List[object]

function Add-Result([string]$module, [string]$step, [bool]$ok, [string]$detail, [object]$extra = $null) {
    $item = [ordered]@{
        module = $module
        step = $step
        ok = $ok
        detail = $detail
    }
    if ($null -ne $extra) {
        $item.extra = $extra
    }
    $results.Add([pscustomobject]$item) | Out-Null
}

function Invoke-CurlMultipartJson([string]$url, [string]$token, [string]$filePath, [string]$category = $null, [string]$contentType = $null) {
    $filePart = if ($contentType) { "file=@$filePath;type=$contentType" } else { "file=@$filePath" }
    $args = @('-s', '-X', 'POST', $url, '-H', "Authorization: $token", '-F', $filePart)
    if ($category) {
        $args += @('-F', "category=$category")
    }
    $resp = & curl.exe @args
    if ([string]::IsNullOrWhiteSpace($resp)) {
        throw "empty response from $url"
    }
    try {
        return $resp | ConvertFrom-Json
    } catch {
        throw "invalid json response from ${url}: $resp"
    }
}

$userName = 'win_verify_' + (Get-Date -Format 'yyyyMMddHHmmss')
$password = 'Passw0rd!'
$token = $null
$headers = @{}
$docId = $null
$templateId = $null
$fileId = $null

try {
    $authBody = @{ username = $userName; password = $password } | ConvertTo-Json
    $auth = Invoke-RestMethod -Method Post -Uri "$userBase/users/auth" -ContentType 'application/json' -Body $authBody
    $token = $auth.data.token
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw 'token is empty'
    }
    $headers = @{ Authorization = $token }
    Add-Result 'user-service' 'POST /users/auth' $true ("userId=" + $auth.data.userId) @{ userName = $userName }
} catch {
    Add-Result 'user-service' 'POST /users/auth' $false $_.Exception.Message
}

if ($token) {
    try {
        $info = Invoke-RestMethod -Method Get -Uri "$userBase/users/info" -Headers $headers
        Add-Result 'user-service' 'GET /users/info' $true ("username=" + $info.data.username)
    } catch {
        Add-Result 'user-service' 'GET /users/info' $false $_.Exception.Message
    }

    try {
        $upload = Invoke-CurlMultipartJson -url "$fileBase/files/upload/single" -token $token -filePath $templateFile -category 'excel' -contentType 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        $fileId = $upload.data.fileId
        $ok = ($upload.code -eq 200) -and (-not [string]::IsNullOrWhiteSpace([string]$fileId))
        Add-Result 'file-service' 'POST /files/upload/single' $ok ("fileId=" + $fileId)
    } catch {
        Add-Result 'file-service' 'POST /files/upload/single' $false $_.Exception.Message
    }

    try {
        $list = Invoke-RestMethod -Method Get -Uri "$fileBase/files/list?pageNum=1&pageSize=10" -Headers $headers
        $records = @($list.data.records).Count
        Add-Result 'file-service' 'GET /files/list' $true ("records=" + $records)
    } catch {
        Add-Result 'file-service' 'GET /files/list' $false $_.Exception.Message
    }

    try {
        $sourceUpload = Invoke-CurlMultipartJson -url "$aiBase/source/upload" -token $token -filePath $sourceFile -contentType 'text/markdown'
        $docId = $sourceUpload.data.id
        $status = $sourceUpload.data.uploadStatus
        $ok = ($sourceUpload.code -eq 200) -and ($status -eq 'parsed')
        $detail = "docId=$docId,status=$status"
        Add-Result 'ai-service(source)' 'POST /source/upload' $ok $detail
    } catch {
        Add-Result 'ai-service(source)' 'POST /source/upload' $false $_.Exception.Message
    }

    try {
        $documents = Invoke-RestMethod -Method Get -Uri "$aiBase/source/documents" -Headers $headers
        Add-Result 'ai-service(source)' 'GET /source/documents' $true ("documents=" + @($documents.data).Count)
    } catch {
        Add-Result 'ai-service(source)' 'GET /source/documents' $false $_.Exception.Message
    }

    if ($docId) {
        try {
            $fields = Invoke-RestMethod -Method Get -Uri "$aiBase/source/$docId/fields" -Headers $headers
            $fieldCount = @($fields.data).Count
            $ok = $fieldCount -gt 0
            Add-Result 'ai-service(source)' 'GET /source/{id}/fields' $ok ("fields=" + $fieldCount)
        } catch {
            Add-Result 'ai-service(source)' 'GET /source/{id}/fields' $false $_.Exception.Message
        }
    }

    try {
        $templateUpload = Invoke-CurlMultipartJson -url "$aiBase/template/upload" -token $token -filePath $templateFile -contentType 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        $templateId = $templateUpload.data.id
        $ok = ($templateUpload.code -eq 200) -and (-not [string]::IsNullOrWhiteSpace([string]$templateId))
        Add-Result 'ai-service(template)' 'POST /template/upload' $ok ("templateId=" + $templateId)
    } catch {
        Add-Result 'ai-service(template)' 'POST /template/upload' $false $_.Exception.Message
    }

    if ($templateId) {
        try {
            $parsed = Invoke-RestMethod -Method Post -Uri "$aiBase/template/$templateId/parse" -Headers $headers
            $slotCount = @($parsed.data).Count
            $ok = $slotCount -gt 0
            Add-Result 'ai-service(template)' 'POST /template/{id}/parse' $ok ("slots=" + $slotCount)
        } catch {
            Add-Result 'ai-service(template)' 'POST /template/{id}/parse' $false $_.Exception.Message
        }

        try {
            $docIds = @()
            if ($docId) { $docIds = @($docId) }
            $fillBody = @{ docIds = $docIds } | ConvertTo-Json
            $fill = Invoke-RestMethod -Method Post -Uri "$aiBase/template/$templateId/fill" -Headers $headers -ContentType 'application/json' -Body $fillBody
            $filledCount = [int]$fill.data.filledCount
            $blankCount = [int]$fill.data.blankCount
            $ok = $filledCount -gt 0
            Add-Result 'ai-service(template)' 'POST /template/{id}/fill' $ok ("filled=$filledCount,blank=$blankCount")
        } catch {
            Add-Result 'ai-service(template)' 'POST /template/{id}/fill' $false $_.Exception.Message
        }

        try {
            Invoke-WebRequest -Method Get -Uri "$aiBase/template/$templateId/download" -Headers $headers -OutFile $outDownload | Out-Null
            $size = (Get-Item $outDownload).Length
            Add-Result 'ai-service(template)' 'GET /template/{id}/download' ($size -gt 0) ("size=" + $size)
        } catch {
            Add-Result 'ai-service(template)' 'GET /template/{id}/download' $false $_.Exception.Message
        }
    }

    try {
        $requests = Invoke-RestMethod -Method Get -Uri "$aiBase/ai/requests?pageNum=1&pageSize=5" -Headers $headers
        $records = @($requests.data.records).Count
        Add-Result 'ai-service(chat)' 'GET /ai/requests' $true ("records=" + $records)
    } catch {
        Add-Result 'ai-service(chat)' 'GET /ai/requests' $false $_.Exception.Message
    }
}

try {
    if (-not $token) {
        throw 'token not available'
    }
    $provider = Invoke-RestMethod -Method Get -Uri "$aiBase/llm/providers/current" -Headers @{ Authorization = $token }
    Add-Result 'ai-service(llm)' 'GET /llm/providers/current' $true ("provider=" + $provider.data.currentProvider)
} catch {
    Add-Result 'ai-service(llm)' 'GET /llm/providers/current' $false $_.Exception.Message
}

try {
    $gw = Invoke-WebRequest -Method Post -Uri "$gatewayBase/users/auth" -ContentType 'application/json' -Body (@{ username = $userName; password = $password } | ConvertTo-Json)
    Add-Result 'gateway-service' 'POST /api/v1/users/auth' $true ("status=" + $gw.StatusCode)
} catch {
    Add-Result 'gateway-service' 'POST /api/v1/users/auth' $false $_.Exception.Message
}

$summary = [ordered]@{
    runAt = (Get-Date).ToString('s')
    gateway = $gatewayBase
    total = $results.Count
    passed = @($results | Where-Object { $_.ok }).Count
    failed = @($results | Where-Object { -not $_.ok }).Count
    results = $results
}

$summary | ConvertTo-Json -Depth 6 | Set-Content -Path $outJson -Encoding UTF8
Write-Host "Smoke test finished: $outJson"
