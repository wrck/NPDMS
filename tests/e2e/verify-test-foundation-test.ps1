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

$temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("pms-test-foundation-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null

try {
    $validRecord = [ordered]@{
        taskId = 'T-CP-009'
        frIds = @('FR-PLT-010')
        acceptanceIds = @('T-CP-009-AC-001')
        testType = 'unit'
        command = 'pwsh -NoProfile -File tests/e2e/verify-test-foundation.ps1 -Type unit'
        result = 'passed'
        executedAt = '2026-07-29T00:00:00Z'
        evidence = @('pom.xml')
    }
    $validFile = Join-Path $temporaryDirectory 'valid.json'
    $validRecord | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $validFile -Encoding utf8

    Test-TraceEvidenceFile -Path $validFile | Out-Null

    $missingField = [ordered]@{}
    foreach ($key in $validRecord.Keys) {
        if ($key -ne 'frIds') { $missingField[$key] = $validRecord[$key] }
    }
    $missingFieldFile = Join-Path $temporaryDirectory 'missing-field.json'
    $missingField | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $missingFieldFile -Encoding utf8
    Assert-Throws -Because 'FR IDs are mandatory' -Action { Test-TraceEvidenceFile -Path $missingFieldFile }

    $invalidStatus = [ordered]@{}
    foreach ($key in $validRecord.Keys) { $invalidStatus[$key] = $validRecord[$key] }
    $invalidStatus.result = 'pending'
    $invalidStatusFile = Join-Path $temporaryDirectory 'invalid-status.json'
    $invalidStatus | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $invalidStatusFile -Encoding utf8
    Assert-Throws -Because 'unknown results must not be accepted as evidence' -Action { Test-TraceEvidenceFile -Path $invalidStatusFile }

    $missingEvidence = [ordered]@{}
    foreach ($key in $validRecord.Keys) { $missingEvidence[$key] = $validRecord[$key] }
    $missingEvidence.evidence = @(Join-Path $temporaryDirectory 'does-not-exist.txt')
    $missingEvidenceFile = Join-Path $temporaryDirectory 'missing-evidence.json'
    $missingEvidence | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $missingEvidenceFile -Encoding utf8
    Assert-Throws -Because 'evidence paths must resolve to real files' -Action { Test-TraceEvidenceFile -Path $missingEvidenceFile }

    $types = @(Get-TestSuiteDefinition | ForEach-Object { $_.TestType })
    $expectedTypes = @('unit', 'integration', 'contract', 'e2e', 'performance', 'security')
    if ((@($types | Sort-Object) -join ',') -ne (@($expectedTypes | Sort-Object) -join ',')) {
        throw "Expected six health-test categories, got: $($types -join ', ')"
    }

    foreach ($jvmType in @('unit', 'integration', 'contract')) {
        $definition = Get-TestSuiteDefinition | Where-Object { $_.TestType -eq $jvmType }
        if ([string]::IsNullOrWhiteSpace($definition.MavenTest)) {
            throw "$jvmType health check must declare the concrete Maven test it executes"
        }
    }

    $jvmRunner = Get-JvmHealthRunner
    if ($jvmRunner.FilePath -notmatch 'mvn(\.cmd|\.exe)?$') {
        throw 'JVM health checks must use the local Maven runtime'
    }
    if (-not (Test-Path -LiteralPath $jvmRunner.JavaHome)) {
        throw 'JVM health checks must point to an existing JDK 25 home'
    }
    if ($jvmRunner.FixturePom -ne 'tests/maven-fixture/pom.xml') {
        throw 'JVM health checks must execute the classification fixture inherited from the real root POM'
    }

    Write-Output 'verify-test-foundation tests passed'
} finally {
    Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
