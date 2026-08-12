[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$loginBody = @{
    username = "tcp006invalid"
    password = "invalid"
    rememberMe = $false
} | ConvertTo-Json -Compress
$loginResponse = Invoke-WebRequest -UseBasicParsing -Method Post `
    -Uri "http://localhost:18081/admin-api/system/auth/login" `
    -ContentType "application/json" `
    -Body $loginBody
if ($loginResponse.Content -is [byte[]]) {
    $loginContent = [Text.Encoding]::UTF8.GetString($loginResponse.Content)
} else {
    $loginContent = [string]$loginResponse.Content
}
$loginResult = $loginContent | ConvertFrom-Json
if ($loginResult.code -ne 1002000000) {
    throw "Invalid credentials must reach normal authentication; actual response: $loginContent"
}

$healthResponse = Invoke-WebRequest -UseBasicParsing `
    -Uri "http://localhost:58080/actuator/health"
if ($healthResponse.StatusCode -ne 200) {
    throw "Backend health check failed: HTTP $($healthResponse.StatusCode)"
}

$proxyResponse = Invoke-WebRequest -UseBasicParsing `
    -Uri "http://localhost:18081/admin-api/system/auth/get-permission-info"
if ($proxyResponse.Headers["Content-Type"] -notmatch "^application/json") {
    throw "Same-origin API proxy no longer returns backend JSON."
}

Write-Output "Single-tenant Docker login verification passed."
