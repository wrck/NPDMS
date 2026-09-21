$ErrorActionPreference = 'Stop'
$platformRoot = Split-Path $PSScriptRoot -Parent
$releaseRoot = Join-Path $platformRoot 'parser-releases/device-command-output-1.0.0'
$outputRoot = Join-Path $platformRoot 'target/management-browser-fixture'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$readJson = { param($name) [System.IO.File]::ReadAllText((Join-Path $releaseRoot $name)) }
$payload = [ordered]@{
    releaseId = 'management-browser-fixture-v1'
    manifest = (& $readJson 'manifest.json' | ConvertFrom-Json)
    rulesJson = & $readJson 'rules.json'
    projectionsJson = & $readJson 'projections.json'
    verificationCases = @(@{
        caseId = 'management-browser-golden'
        inputContent = & $readJson 'input.json'
        expectedResultJson = & $readJson 'expected-result.json'
    })
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
$outputPath = Join-Path $outputRoot 'release-request.json'
[System.IO.File]::WriteAllText($outputPath, ($payload | ConvertTo-Json -Depth 30), $utf8)
Write-Host "Prepared offline fixture: $outputPath"
Write-Host 'No API calls were made. Import only into an isolated acceptance database; never a live deployment.'
