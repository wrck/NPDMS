[CmdletBinding()]
param(
    [switch]$FromUnified,
    [string]$EvidenceDirectory
)

$ErrorActionPreference = 'Stop'
$foundationScript = Join-Path $PSScriptRoot '..\e2e\verify-test-foundation.ps1'
. $foundationScript

function Invoke-TraceEvidenceSecurityProbe {
    $probeScript = Join-Path $PSScriptRoot 'trace-evidence-security-test.ps1'
    Invoke-HealthCommand -Name 'trace evidence security probe' -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoProfile', '-File', $probeScript)
}

if ($FromUnified) {
    Invoke-TraceEvidenceSecurityProbe
    Write-Output 'security health check passed'
    return
}

& $foundationScript -Type security -EvidenceDirectory $EvidenceDirectory
