$ErrorActionPreference = 'Stop'
$platformRoot = $PSScriptRoot
$webRoot = Join-Path $platformRoot 'device-ops-web'
$jarPath = Join-Path $platformRoot 'device-ops-server\target\device-ops-server.jar'

function Invoke-Checked([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE" }
}

Push-Location $webRoot
try {
    Invoke-Checked 'pnpm.cmd' @('install', '--frozen-lockfile')
    Invoke-Checked 'pnpm.cmd' @('test')
    Invoke-Checked 'pnpm.cmd' @('ts:check')
    Invoke-Checked 'pnpm.cmd' @('lint')
    Invoke-Checked 'pnpm.cmd' @('build')
} finally {
    Pop-Location
}

Push-Location $platformRoot
try {
    Invoke-Checked 'mvn.cmd' @('clean', 'verify')
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $jarPath)) { throw "Executable JAR was not produced: $jarPath" }
$staticEntry = & jar tf $jarPath | Select-String -SimpleMatch 'BOOT-INF/classes/static/index.html'
if (-not $staticEntry) { throw 'Executable JAR does not contain static/index.html' }
Write-Host "Built $jarPath"
