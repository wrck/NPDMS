$ErrorActionPreference = 'Stop'

$foundationScript = Join-Path $PSScriptRoot 'verify-test-foundation.ps1'
. $foundationScript

function Assert-Throws {
    param(
        [scriptblock]$Action,
        [string]$Because
    )

    try {
        & $Action
    } catch {
        return
    }

    throw "Expected failure: $Because"
}

$powershellExecutable = (Get-Process -Id $PID).Path

Invoke-HealthCommand -Name 'successful probe' -FilePath $powershellExecutable -ArgumentList @('-NoProfile', '-Command', 'exit 0')

Assert-Throws -Because 'a failed child test command must fail its health check' -Action {
    Invoke-HealthCommand -Name 'failed probe' -FilePath $powershellExecutable -ArgumentList @('-NoProfile', '-Command', 'exit 7')
}

Write-Output 'verify-test-foundation health command tests passed'
