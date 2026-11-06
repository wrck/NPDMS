param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$DotnetHost,
    [string]$RoslynCompiler,
    [string]$FrameworkReferences,
    [string]$PortableReferences,
    [string]$PortableRuntimeVersion
)
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $repo 'scripts/worktree-toolchain.ps1')
$bridgeLock = Enter-WorktreeBuildLock
try {
    if (Test-Path -LiteralPath $OutputDirectory) { throw 'Choose a new bridge output directory' }
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    $out = (Resolve-Path -LiteralPath $OutputDirectory).Path
    $sources = @((Join-Path $repo 'device-ops-platform/packaging/windows/ClientConfiguration.cs'),
        (Join-Path $PSScriptRoot '../packaging/windows/ServiceBridge.cs'),
        (Join-Path $PSScriptRoot '../packaging/windows/ServiceOwnership.cs'))
    $references = @('mscorlib', 'System', 'System.Core', 'System.Xml', 'System.Xml.Linq', 'System.Security', 'System.ServiceProcess', 'System.Web.Extensions')
    if ($IsLinux -or $IsMacOS) {
        foreach ($item in @($DotnetHost, $RoslynCompiler, $FrameworkReferences)) {
            if (!$item -or !(Test-Path -LiteralPath $item)) { throw 'Explicit Roslyn and .NET Framework reference paths required for compile-only diagnostics' }
        }
        $compiler = $DotnetHost
        $prefix = @($RoslynCompiler, '/nostdlib+', '/deterministic+')
        $refs = @($references | ForEach-Object { '/r:' + (Join-Path $FrameworkReferences ($_ + '.dll')) })
    } else {
        $compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
        $prefix = @()
        $refs = @($references | Where-Object { $_ -ne 'mscorlib' } | ForEach-Object { '/r:' + $_ + '.dll' })
    }
    foreach ($name in @('NpdmsServiceBridge', 'ServiceBridgeTests')) {
        $buildSources = @($sources)
        $entry = 'Npdms.Dac.Desktop.ServiceBridge'
        if ($name -eq 'ServiceBridgeTests') {
            $buildSources += Join-Path $PSScriptRoot '../packaging/windows/ServiceBridgeTests.cs'
            $buildSources += Join-Path $PSScriptRoot '../packaging/windows/ServiceOwnershipTests.cs'
            $entry = 'Npdms.Dac.Desktop.ServiceBridgeTests'
        }
        & $compiler @prefix /nologo /optimize+ /langversion:5 /platform:x64 /target:exe "/main:$entry" "/out:$(Join-Path $out ($name + '.exe'))" @refs @buildSources
        if ($LASTEXITCODE -ne 0) { throw 'Windows bridge compilation failed' }
    }
    $tested = $false
    $portableTested = $false
    if ($PortableReferences) {
        if (!$DotnetHost -or !$RoslynCompiler -or $PortableRuntimeVersion -notmatch '^8\.0\.[0-9]+$') { throw 'Explicit .NET 8 runtime required for portable decision tests' }
        $portableRefs = @(Get-ChildItem -LiteralPath $PortableReferences -Filter '*.dll' | ForEach-Object { '/r:' + $_.FullName })
        & $DotnetHost $RoslynCompiler /nologo /nostdlib+ /langversion:5 /target:exe "/out:$(Join-Path $out 'ServiceOwnershipTests.dll')" @portableRefs (Join-Path $PSScriptRoot '../packaging/windows/ServiceOwnership.cs') (Join-Path $PSScriptRoot '../packaging/windows/ServiceOwnershipTests.cs')
        if ($LASTEXITCODE -ne 0) { throw 'Portable ownership tests compilation failed' }
        $runtime = @{ runtimeOptions = @{ tfm = 'net8.0'; framework = @{ name = 'Microsoft.NETCore.App'; version = $PortableRuntimeVersion } } } | ConvertTo-Json -Depth 4
        [IO.File]::WriteAllText((Join-Path $out 'ServiceOwnershipTests.runtimeconfig.json'), $runtime, (New-Object Text.UTF8Encoding($false)))
        & $DotnetHost (Join-Path $out 'ServiceOwnershipTests.dll')
        if ($LASTEXITCODE -ne 0) { throw 'Portable ownership decision tests failed' }
        $portableTested = $true
    }
    if (!$IsLinux -and !$IsMacOS) {
        & (Join-Path $out 'ServiceBridgeTests.exe')
        if ($LASTEXITCODE -ne 0) { throw 'Windows ACL boundary tests failed' }
        $tested = $true
    }
    $helper = Join-Path $out 'NpdmsServiceBridge.exe'
    $record = [ordered]@{ format = 1; bytes = (Get-Item -LiteralPath $helper).Length;
        sha256 = (Get-FileHash -LiteralPath $helper -Algorithm SHA256).Hash.ToLowerInvariant();
        windowsAclTestsExecuted = $tested; realServiceIntegrationTested = $false;
        ownershipDecisionTestsExecuted = $portableTested -or $tested; ownershipBoundary = 'scm-process-listener';
        serviceMode = 'existing-machine-service'; releaseFrozen = $false
    } | ConvertTo-Json
    [IO.File]::WriteAllText((Join-Path $out 'bridge-manifest.json'), $record, (New-Object Text.UTF8Encoding($false)))
} finally { $bridgeLock.Dispose() }
