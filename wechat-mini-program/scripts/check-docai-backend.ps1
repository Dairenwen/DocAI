param(
    [string[]]$BaseUrls = @(
        'http://127.0.0.1:8080/api/v1',
        'http://127.0.0.1:18080/api/v1'
    ),
    [string]$SourceFile = '',
    [string]$TemplateFile = '',
    [int]$RequestTimeoutSec = 180,
    [int]$PollTimeoutSec = 240,
    [int]$PollIntervalSec = 5
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $repoRoot
$docAiRoot = Join-Path $workspaceRoot 'DocAI'

function Normalize-BaseUrl([string]$url) {
    if ([string]::IsNullOrWhiteSpace($url)) {
        return ''
    }

    return $url.TrimEnd('/')
}

function Resolve-DefaultFile([string]$explicitPath, [string[]]$searchRoots, [string[]]$patterns, [string]$label) {
    if ($explicitPath) {
        if (Test-Path $explicitPath) {
            return (Resolve-Path $explicitPath).Path
        }
        throw "$label not found: $explicitPath"
    }

    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) {
            continue
        }

        foreach ($pattern in $patterns) {
            $candidate = Get-ChildItem -Path $root -Filter $pattern -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($candidate) {
                return $candidate.FullName
            }
        }
    }

    throw "Unable to locate a default $label. Please pass -$label <path>."
}

function Test-TcpPort([string]$hostName, [int]$port, [int]$timeoutMs = 3000) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($hostName, $port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($timeoutMs, $false)) {
            return $false
        }

        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Test-ApiShape([string]$baseUrl) {
    try {
        $probe = Invoke-WebRequest -Uri ($baseUrl + '/users/info') -Method Get -TimeoutSec 10 -UseBasicParsing
        $json = $probe.Content | ConvertFrom-Json
        if ($null -ne $json.code) {
            return $true
        }
        return $false
    } catch {
        if ($_.Exception.Response) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
                $json = $body | ConvertFrom-Json
                return $null -ne $json.code
            } catch {
                return $false
            }
        }
        return $false
    }
}

function Resolve-WorkingBaseUrl([string[]]$candidates) {
    foreach ($candidate in $candidates) {
        $baseUrl = Normalize-BaseUrl $candidate
        if (-not $baseUrl) {
            continue
        }

        $uri = [Uri]$baseUrl
        $port = if ($uri.IsDefaultPort) {
            if ($uri.Scheme -eq 'https') { 443 } else { 80 }
        } else {
            $uri.Port
        }

        if (-not (Test-TcpPort -hostName $uri.Host -port $port)) {
            Write-Host "[SKIP] $baseUrl tcp unreachable"
            continue
        }

        if (-not (Test-ApiShape -baseUrl $baseUrl)) {
            Write-Host "[SKIP] $baseUrl does not return DocAI JSON"
            continue
        }

        return $baseUrl
    }

    throw "No reachable DocAI API base URL found in: $($candidates -join ', ')"
}

function Assert-ApiSuccess($response, [string]$stepName) {
    if ($null -eq $response) {
        throw "$stepName returned an empty response."
    }

    if ($response.code -ne 200) {
        $message = if ($response.message) { $response.message } else { 'unknown error' }
        throw "$stepName failed: $message"
    }
}

function Invoke-JsonApi {
    param(
        [string]$Url,
        [string]$Method = 'GET',
        [object]$Body = $null,
        [string]$Token = '',
        [int]$TimeoutSec = 60
    )

    $headers = @{}
    if ($Token) {
        $headers['Authorization'] = 'Bearer ' + $Token
    }

    $params = @{
        Uri = $Url
        Method = $Method
        Headers = $headers
        TimeoutSec = $TimeoutSec
        UseBasicParsing = $true
    }

    if ($null -ne $Body) {
        $params['ContentType'] = 'application/json'
        $params['Body'] = ($Body | ConvertTo-Json -Depth 10)
    }

    return Invoke-RestMethod @params
}

function Invoke-MultipartApi {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$Token = '',
        [string]$FieldName = 'file',
        [int]$TimeoutSec = 60
    )

    Add-Type -AssemblyName System.Net.Http

    $client = New-Object System.Net.Http.HttpClient
    $stream = $null
    $content = $null

    try {
        $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)
        if ($Token) {
            $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
        }

        $stream = [System.IO.File]::OpenRead($FilePath)
        $streamContent = New-Object System.Net.Http.StreamContent($stream)
        $content = New-Object System.Net.Http.MultipartFormDataContent
        $content.Add($streamContent, $FieldName, [System.IO.Path]::GetFileName($FilePath))

        $response = $client.PostAsync($Url, $content).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "HTTP $([int]$response.StatusCode): $body"
        }

        return $body | ConvertFrom-Json
    } finally {
        if ($content) {
            $content.Dispose()
        }
        if ($stream) {
            $stream.Dispose()
        }
        $client.Dispose()
    }
}

function Wait-DocumentParsed {
    param(
        [string]$BaseUrl,
        [string]$Token,
        [long]$DocumentId,
        [int]$TimeoutSec,
        [int]$IntervalSec
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)

    while ((Get-Date) -lt $deadline) {
        $statusRes = Invoke-JsonApi -Url ($BaseUrl + '/source/documents/status') -Token $Token
        Assert-ApiSuccess -response $statusRes -stepName 'source status poll'

        $doc = @($statusRes.data) | Where-Object { [string]$_.id -eq [string]$DocumentId } | Select-Object -First 1
        if ($doc) {
            $status = [string]$doc.uploadStatus
            if ($status -eq 'parsed') {
                return $doc
            }
            if ($status -eq 'failed') {
                throw "Source document parsing failed for document $DocumentId."
            }
        }

        Start-Sleep -Seconds $IntervalSec
    }

    throw "Timed out waiting for source document $DocumentId to become parsed."
}

$SourceFile = Resolve-DefaultFile -explicitPath $SourceFile -searchRoots @(
    (Join-Path $repoRoot 'scripts\fixtures'),
    (Join-Path $docAiRoot 'data\local-oss\source_documents'),
    (Join-Path $docAiRoot 'test-data\data\local-oss\source_documents'),
    (Join-Path $docAiRoot 'docai-pro\data\local-oss\source_documents')
) -patterns @('backend-check-source.txt', '*.txt', '*.docx', '*.md', '*.xlsx') -label 'SourceFile'

$TemplateFile = Resolve-DefaultFile -explicitPath $TemplateFile -searchRoots @(
    (Join-Path $docAiRoot 'data\local-oss\template_files'),
    (Join-Path $docAiRoot 'test-data\data\local-oss\template_files'),
    (Join-Path $docAiRoot 'docai-pro\data\local-oss\template_files')
) -patterns @('*.xlsx', '*.docx') -label 'TemplateFile'

$baseUrl = Resolve-WorkingBaseUrl -candidates $BaseUrls
Write-Host "[OK] Base URL: $baseUrl"

$username = 'wxmini_check_' + [DateTimeOffset]::Now.ToUnixTimeSeconds()
$password = 'Pass@123456'

$authRes = Invoke-JsonApi -Url ($baseUrl + '/users/auth') -Method 'POST' -Body @{
    username = $username
    password = $password
    isRegister = $true
} -TimeoutSec $RequestTimeoutSec
Assert-ApiSuccess -response $authRes -stepName 'user auth'
$token = [string]$authRes.data.token
if (-not $token) {
    throw 'Auth succeeded but token is empty.'
}
Write-Host "[OK] Auth: $username"

$userInfoRes = Invoke-JsonApi -Url ($baseUrl + '/users/info') -Token $token -TimeoutSec $RequestTimeoutSec
Assert-ApiSuccess -response $userInfoRes -stepName 'user info'
Write-Host "[OK] User info"

$sourceUploadRes = Invoke-MultipartApi -Url ($baseUrl + '/source/upload') -FilePath $SourceFile -Token $token -TimeoutSec $RequestTimeoutSec
Assert-ApiSuccess -response $sourceUploadRes -stepName 'source upload'
$documentId = [long]$sourceUploadRes.data.id
if (-not $documentId) {
    throw 'Source upload succeeded but document id is empty.'
}
Write-Host "[OK] Source upload: $documentId"

$parsedDoc = Wait-DocumentParsed -BaseUrl $baseUrl -Token $token -DocumentId $documentId -TimeoutSec $PollTimeoutSec -IntervalSec $PollIntervalSec
Write-Host "[OK] Source parsed"

$templateUploadRes = Invoke-MultipartApi -Url ($baseUrl + '/template/upload') -FilePath $TemplateFile -Token $token -TimeoutSec $RequestTimeoutSec
Assert-ApiSuccess -response $templateUploadRes -stepName 'template upload'
$templateId = [long]$templateUploadRes.data.id
if (-not $templateId) {
    throw 'Template upload succeeded but template id is empty.'
}
Write-Host "[OK] Template upload: $templateId"

$templateParseRes = Invoke-JsonApi -Url ($baseUrl + "/template/$templateId/parse") -Method 'POST' -Body @{} -Token $token -TimeoutSec $RequestTimeoutSec
Assert-ApiSuccess -response $templateParseRes -stepName 'template parse'
$slotCount = @($templateParseRes.data).Count
Write-Host "[OK] Template parse: $slotCount slots"

$fillRes = Invoke-JsonApi -Url ($baseUrl + "/template/$templateId/fill") -Method 'POST' -Body @{
    docIds = @($documentId)
    userRequirement = 'Use the uploaded source document only.'
} -Token $token -TimeoutSec $RequestTimeoutSec
Assert-ApiSuccess -response $fillRes -stepName 'template fill'
Write-Host "[OK] Template fill"

$summary = [ordered]@{
    checkedAt = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    baseUrl = $baseUrl
    username = $username
    sourceFile = $SourceFile
    templateFile = $TemplateFile
    documentId = $documentId
    templateId = $templateId
    sourceStatus = $parsedDoc.uploadStatus
    slotCount = $slotCount
    fillResult = $fillRes.data
}

$summary | ConvertTo-Json -Depth 10
