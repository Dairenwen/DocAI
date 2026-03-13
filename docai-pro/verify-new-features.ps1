$ErrorActionPreference = 'Stop'

$base = 'http://localhost:18080/api/v1'
$user = 'feature_user_' + (Get-Date -Format 'HHmmss')
$password = 'Passw0rd!'
$email = 'feature_user@test.com'
$results = New-Object System.Collections.Generic.List[string]

function Add-Result([string]$k, [string]$v) {
    $results.Add("${k}: ${v}") | Out-Null
}

$auth = Invoke-RestMethod -Method Post -Uri "$base/users/auth" -ContentType 'application/json' -Body (@{ username = $user; password = $password } | ConvertTo-Json)
$token = $auth.data.token
Add-Result 'login_password' ([string]$auth.code)

$send = Invoke-RestMethod -Method Post -Uri "$base/users/verification-code" -ContentType 'application/json' -Body (@{ email = $email } | ConvertTo-Json)
Add-Result 'send_code_delivery' ([string]$send.data.deliveryMode)
Add-Result 'send_code_success' ([string]$send.data.sendSuccess)

$codeKey = docker exec docai-redis redis-cli --raw keys 'code:*' | Select-Object -First 1
$code = $codeKey -replace '^code:', ''
$emailAuth = Invoke-RestMethod -Method Post -Uri "$base/users/auth" -ContentType 'application/json' -Body (@{ email = $email; verificationCode = $code; username = 'mail_user' } | ConvertTo-Json)
Add-Result 'email_auth_code' ([string]$emailAuth.code)
Add-Result 'email_auth_user' ([string]$emailAuth.data.userName)

$send2 = Invoke-RestMethod -Method Post -Uri "$base/users/verification-code" -ContentType 'application/json' -Body (@{ email = $email } | ConvertTo-Json)
$codeKey2 = docker exec docai-redis redis-cli --raw keys 'code:*' | Select-Object -First 1
$code2 = $codeKey2 -replace '^code:', ''
$reset = Invoke-RestMethod -Method Post -Uri "$base/users/password/reset-by-email" -ContentType 'application/json' -Body (@{ email = $email; verificationCode = $code2; newPassword = 'NewPassw0rd!'; confirmPassword = 'NewPassw0rd!' } | ConvertTo-Json)
Add-Result 'reset_password_code' ([string]$reset.code)

$relogin = Invoke-RestMethod -Method Post -Uri "$base/users/auth" -ContentType 'application/json' -Body (@{ username = 'mail_user'; password = 'NewPassw0rd!' } | ConvertTo-Json)
Add-Result 'relogin_new_password' ([string]$relogin.code)

$llmList = Invoke-RestMethod -Method Get -Uri "$base/llm/providers/list" -Headers @{ Authorization = "Bearer $token" }
Add-Result 'llm_providers_count' ([string]@($llmList.data).Count)

$switch = Invoke-RestMethod -Method Post -Uri "$base/llm/providers/switch" -Headers @{ Authorization = "Bearer $token" } -ContentType 'application/json' -Body (@{ providerName = 'deepseek' } | ConvertTo-Json)
Add-Result 'llm_switch_to' ([string]$switch.data.currentProvider)

$current = Invoke-RestMethod -Method Get -Uri "$base/llm/providers/current" -Headers @{ Authorization = "Bearer $token" }
Add-Result 'llm_current' ([string]$current.data.currentProvider)

$sourceRespPath = 'F:\DocAI\tmp_source_resp.txt'
curl.exe -s -X POST "$base/source/upload" -H "Authorization: Bearer $token" -F "file=@F:/DocAI/test-data/source.md;type=text/markdown" -o $sourceRespPath | Out-Null
$sourceRaw = Get-Content $sourceRespPath -Raw
$docId = [regex]::Match($sourceRaw, '"id":(\d+)').Groups[1].Value
$uploadStatus = [regex]::Match($sourceRaw, '"uploadStatus":"([^"]+)"').Groups[1].Value
Add-Result 'source_upload_status' $uploadStatus
Add-Result 'source_doc_id' $docId

$fields = Invoke-RestMethod -Method Get -Uri "$base/source/$docId/fields" -Headers @{ Authorization = "Bearer $token" }
Add-Result 'source_fields_count' ([string]@($fields.data).Count)

$templateRespPath = 'F:\DocAI\tmp_template_resp.txt'
curl.exe -s -X POST "$base/template/upload" -H "Authorization: Bearer $token" -F "file=@F:/DocAI/test-data/template.xlsx;type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -o $templateRespPath | Out-Null
$templateRaw = Get-Content $templateRespPath -Raw
$templateId = [regex]::Match($templateRaw, '"id":(\d+)').Groups[1].Value
Add-Result 'template_id' $templateId

$parse = Invoke-RestMethod -Method Post -Uri "$base/template/$templateId/parse" -Headers @{ Authorization = "Bearer $token" }
Add-Result 'template_slots' ([string]@($parse.data).Count)

$fill = Invoke-RestMethod -Method Post -Uri "$base/template/$templateId/fill" -Headers @{ Authorization = "Bearer $token" } -ContentType 'application/json' -Body (@{ docIds = @([long]$docId) } | ConvertTo-Json)
Add-Result 'template_filled' ([string]$fill.data.filledCount)
Add-Result 'template_blank' ([string]$fill.data.blankCount)

$outFile = 'F:\DocAI\docai-pro\logs\verify-new-features.txt'
$results -join "`n" | Set-Content -Path $outFile -Encoding UTF8
Write-Host "Verification written to $outFile"
