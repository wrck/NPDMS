[CmdletBinding()]
param(
    [switch]$FromUnified,
    [string]$EvidenceDirectory
)

$ErrorActionPreference = 'Stop'
$foundationScript = Join-Path $PSScriptRoot '..\e2e\verify-test-foundation.ps1'
. $foundationScript

function Invoke-PerformanceProbe {
    $powershellExecutable = (Get-Process -Id $PID).Path
    $probe = '$stopwatch = [System.Diagnostics.Stopwatch]::StartNew(); 1..10000 | ForEach-Object { [Math]::Sqrt($_) | Out-Null }; $stopwatch.Stop(); if ($stopwatch.ElapsedMilliseconds -gt 10000) { exit 1 }'
    Invoke-HealthCommand -Name 'performance probe' -FilePath $powershellExecutable -ArgumentList @('-NoProfile', '-Command', $probe)
}

if ($FromUnified) {
    Invoke-PerformanceProbe
    Write-Output 'performance health check passed'
    return
}

& $foundationScript -Type performance -EvidenceDirectory $EvidenceDirectory
