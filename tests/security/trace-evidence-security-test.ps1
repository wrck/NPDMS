$ErrorActionPreference = 'Stop'

$foundationScript = Join-Path $PSScriptRoot '..\e2e\verify-test-foundation.ps1'
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

$temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("pms-trace-security-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null

try {
    $evidenceFile = Join-Path $script:RepositoryRoot 'pom.xml'
    $validRecord = [ordered]@{
        taskId = 'T-CP-009'
        frIds = @('FR-PLT-010')
        acceptanceIds = @('T-CP-009-AC-001')
        testType = 'security'
        command = 'powershell.exe -NoProfile -File tests/security/verify-security-health.ps1'
        result = 'passed'
        executedAt = '2026-07-29T00:00:00Z'
        evidence = @('pom.xml')
    }
    $validFile = Join-Path $temporaryDirectory 'valid.json'
    $validRecord | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $validFile -Encoding utf8
    Test-TraceEvidenceFile -Path $validFile | Out-Null

    $escapedRecord = [ordered]@{}
    foreach ($key in $validRecord.Keys) { $escapedRecord[$key] = $validRecord[$key] }
    $escapedRecord.evidence = @('../pom.xml')
    $escapedFile = Join-Path $temporaryDirectory 'escaped.json'
    $escapedRecord | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $escapedFile -Encoding utf8
    Assert-Throws -Because 'evidence outside the repository can be forged' -Action {
        Test-TraceEvidenceFile -Path $escapedFile
    }

    Write-Output 'trace evidence security tests passed'
} finally {
    Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
