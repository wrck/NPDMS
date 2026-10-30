param(
    [Parameter(Mandatory=$true)][string]$PackageDirectory,
    [Parameter(Mandatory=$true)][string]$Compiler,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
. (Join-Path $PSScriptRoot 'worktree-toolchain.ps1')
$innoLock = Enter-WorktreeBuildLock
try {
    $innoPackage = (Resolve-Path -LiteralPath $PackageDirectory).Path
    $innoResult = Get-Content -LiteralPath (Join-Path $innoPackage 'package-result.json') -Raw | ConvertFrom-Json
    if ($innoResult.installerConfigurationValidation -ne 'passed-real-msi-session') { throw 'Validated MSI payload is required.' }
    $innoArtifact = & $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') verify-dac $taskWorkspace $innoResult.sourceManifest
    if ($LASTEXITCODE -ne 0) { throw 'Frozen DAC artifact validation failed.' }
    $innoPayload = Join-Path $innoPackage 'payload'
    if ((Get-FileHash -LiteralPath (Join-Path $innoPayload 'app/device-ops-server.jar')).Hash -ne (Get-FileHash -LiteralPath @($innoArtifact)[-1]).Hash) { throw 'Installer payload does not match frozen DAC.' }
    foreach ($innoFile in (Get-Content -LiteralPath (Join-Path $innoPackage 'package-files.json') -Raw | ConvertFrom-Json)) {
        $innoSource = Join-Path $innoPayload $innoFile.path
        if ((Get-FileHash -LiteralPath $innoSource -Algorithm SHA256).Hash.ToLowerInvariant() -ne $innoFile.sha256) { throw 'Shared installer payload differs.' }
    }
    if (Test-Path -LiteralPath $OutputDirectory) { throw 'Choose a new output directory; existing packages are retained.' }
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    $innoOutput = (Resolve-Path -LiteralPath $OutputDirectory).Path
    & $Compiler "/DPayload=$innoPayload" "/DVersion=$($innoResult.version)" "/DOutputDirectory=$innoOutput" "/DDefaultServerUrl=$($innoResult.defaultServerUrl)" "/DDefaultWebOrigin=$($innoResult.defaultWebOrigin)" (Join-Path $taskWorkspace 'device-ops-platform/packaging/windows/Package.iss') *> (Join-Path $innoOutput 'compiler.log')
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $innoOutput 'compiler.log') -Tail 25; throw 'Inno compilation failed.' }
    $innoInstaller = Join-Path $innoOutput "NPDMS-DAC-$($innoResult.version)-x64-inno.exe"
    [ordered]@{ version=$innoResult.version; installer=$innoInstaller; bytes=(Get-Item -LiteralPath $innoInstaller).Length
        sha256=(Get-FileHash -LiteralPath $innoInstaller -Algorithm SHA256).Hash.ToLowerInvariant()
        sharedPayload=$innoPayload; sourceManifest=$innoResult.sourceManifest; installedOrStarted=$false
        payloadFilesVerified=@(Get-Content -LiteralPath (Join-Path $innoPackage 'package-files.json') -Raw | ConvertFrom-Json).Count
        compilerSha256=(Get-FileHash -LiteralPath $Compiler -Algorithm SHA256).Hash.ToLowerInvariant()
        installerSourceSha256=(Get-FileHash -LiteralPath (Join-Path $taskWorkspace 'device-ops-platform/packaging/windows/Package.iss') -Algorithm SHA256).Hash.ToLowerInvariant()
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $innoOutput 'package-result.json') -Encoding utf8
    Get-Content -LiteralPath (Join-Path $innoOutput 'package-result.json')
} finally { $innoLock.Dispose() }
