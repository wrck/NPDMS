param(
    [Parameter(Mandatory = $true)][string]$Manifest,
    [ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version = '1.1.8',
    [string]$DefaultServerUrl = 'http://10.210.0.11:59191',
    [string]$DefaultWebOrigin = 'http://10.210.0.11:19191',
    [string]$OutputDirectory,
    [string]$WixDirectory,
    [string]$WinSwExecutable,
    [string]$VerifyRunningDesktopDirectory
)

. (Join-Path $PSScriptRoot 'worktree-toolchain.ps1')
$desktopLock = Enter-WorktreeBuildLock
try {
    $desktopManifest = (Resolve-Path -LiteralPath $Manifest).Path
    $desktopArtifact = & $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') verify-dac $taskWorkspace $desktopManifest
    if ($LASTEXITCODE -ne 0) { throw 'Frozen DAC artifact validation failed' }
    $desktopArtifact = @($desktopArtifact)[-1]
    $desktopSource = Join-Path $taskWorkspace 'device-ops-platform/packaging/windows'
    if (!$OutputDirectory) { $OutputDirectory = Join-Path $taskWorkspace ('.run/build/dac-desktop-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
    if (Test-Path -LiteralPath $OutputDirectory) { throw 'Choose a new output directory; existing builds are never overwritten' }
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    $desktopOutput = (Resolve-Path -LiteralPath $OutputDirectory).Path
    $desktopTools = Join-Path $desktopOutput 'tools'
    $desktopPayload = Join-Path $desktopOutput 'payload'
    New-Item -ItemType Directory -Path $desktopTools, $desktopPayload, (Join-Path $desktopPayload 'app'), (Join-Path $desktopOutput 'logs') | Out-Null
    if (!$WixDirectory) {
        $desktopArchive = Join-Path $desktopTools 'wix3141.zip'
        Invoke-WebRequest 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile $desktopArchive
        $WixDirectory = Join-Path $desktopTools 'wix'
        Expand-Archive -LiteralPath $desktopArchive -DestinationPath $WixDirectory
    }
    if (!$WinSwExecutable) {
        $WinSwExecutable = Join-Path $desktopTools 'WinSW.NET461.exe'
        Invoke-WebRequest 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW.NET461.exe' -OutFile $WinSwExecutable
    }
    foreach ($desktopTool in @('candle.exe', 'light.exe', 'heat.exe', 'dark.exe')) {
        if (!(Test-Path -LiteralPath (Join-Path $WixDirectory $desktopTool))) { throw "WiX tool missing: $desktopTool" }
    }
    $desktopCompiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    $desktopReferences = @('/r:System.Core.dll', '/r:System.Xml.Linq.dll', '/r:System.Security.dll', '/r:System.ServiceProcess.dll', '/r:System.Windows.Forms.dll', '/r:System.Drawing.dll', '/r:System.Web.Extensions.dll')
    $desktopCommon = Join-Path $desktopSource 'ClientConfiguration.cs'
    foreach ($desktopBuild in @(
        @{ Name = 'SdpDac'; Source = 'Launcher.cs'; Target = 'winexe' },
        @{ Name = 'SdpDacHost'; Source = 'RuntimeHost.cs'; Target = 'exe' },
        @{ Name = 'ClientConfigurationTests'; Source = 'ClientConfigurationTests.cs'; Target = 'exe' }
    )) {
        $desktopDestination = if ($desktopBuild.Name -eq 'ClientConfigurationTests') { $desktopOutput } else { $desktopPayload }
        $desktopSharedSources = @($desktopCommon, (Join-Path $desktopSource 'BrowserAccessPolicy.cs'))
        if ($desktopBuild.Name -eq 'ClientConfigurationTests') { $desktopSharedSources += (Join-Path $desktopSource 'PreviousInnoInstallation.cs') }
        & $desktopCompiler /nologo /optimize+ /platform:x64 "/target:$($desktopBuild.Target)" "/out:$(Join-Path $desktopDestination ($desktopBuild.Name + '.exe'))" @desktopReferences @desktopSharedSources (Join-Path $desktopSource $desktopBuild.Source)
        if ($LASTEXITCODE -ne 0) { throw 'Native DAC helper compilation failed' }
    }
    & (Join-Path $desktopOutput 'ClientConfigurationTests.exe') (Join-Path $desktopOutput 'configuration-test-data')
    if ($LASTEXITCODE -ne 0) { throw 'Native client boundary tests failed' }
    & $desktopCompiler /nologo /optimize+ /platform:x64 /target:exe /main:Npdms.Dac.Desktop.DesktopStatusLauncherTests "/out:$(Join-Path $desktopOutput 'DesktopStatusLauncherTests.exe')" @desktopReferences $desktopCommon (Join-Path $desktopSource 'BrowserAccessPolicy.cs') (Join-Path $desktopSource 'Launcher.cs') (Join-Path $desktopSource 'DesktopStatusLauncherTests.cs')
    if ($LASTEXITCODE -ne 0) { throw 'Native status regression compilation failed' }
    if ($VerifyRunningDesktopDirectory) {
        & (Join-Path $desktopOutput 'DesktopStatusLauncherTests.exe') $VerifyRunningDesktopDirectory
        if ($LASTEXITCODE -ne 0) { throw 'Read-only running-desktop regression failed; no service changed.' }
    }
    # WiX 3.14.1 SFXCA fails in impersonated execute actions (upstream issue 8687).
    # Use the fixed DTF host without downgrading security or changing machine temp ACLs.
    foreach ($desktopDependency in @(
        @{ Name = 'wixtoolset.dtf.customaction'; Hash = '0E0A762DB83E9DEA9AADB1C0EC3CB80A8757CEF1EFCA6D579682ABC2FA7D779A' },
        @{ Name = 'wixtoolset.dtf.windowsinstaller'; Hash = '9AFBC9C8F0DD5BCB82F50055B5FEFF036E8BF28BD7F3FA8BD6442347B610CCA9' }
    )) {
        $desktopDependencyArchive = Join-Path $desktopTools ($desktopDependency.Name + '.zip')
        Invoke-WebRequest "https://api.nuget.org/v3-flatcontainer/$($desktopDependency.Name)/5.0.2/$($desktopDependency.Name).5.0.2.nupkg" -OutFile $desktopDependencyArchive
        if ((Get-FileHash -LiteralPath $desktopDependencyArchive -Algorithm SHA256).Hash -ne $desktopDependency.Hash) { throw 'Pinned DTF package checksum differs.' }
        Expand-Archive -LiteralPath $desktopDependencyArchive -DestinationPath (Join-Path $desktopTools $desktopDependency.Name)
    }
    $desktopDtf = Join-Path $desktopTools 'wixtoolset.dtf.windowsinstaller/lib/net20/WixToolset.Dtf.WindowsInstaller.dll'
    $desktopDtfTools = Join-Path $desktopTools 'wixtoolset.dtf.customaction/tools'
    $desktopActions = Join-Path $desktopOutput 'InstallerActions.dll'
    $desktopActionsPackage = Join-Path $desktopOutput 'InstallerActions.CA.dll'
    & $desktopCompiler /nologo /optimize+ /platform:x64 /target:library "/out:$desktopActions" @desktopReferences "/r:$desktopDtf" $desktopCommon (Join-Path $desktopSource 'BrowserAccessPolicy.cs') (Join-Path $desktopSource 'PreviousInnoInstallation.cs') (Join-Path $desktopSource 'InstallerActions.cs')
    if ($LASTEXITCODE -ne 0) { throw 'Installer validation compilation failed' }
    & (Join-Path $desktopDtfTools 'WixToolset.Dtf.MakeSfxCA.exe') $desktopActionsPackage (Join-Path $desktopDtfTools 'x64/SfxCA.dll') $desktopActions (Join-Path $desktopSource 'CustomAction.config') $desktopDtf
    if ($LASTEXITCODE -ne 0) { throw 'Installer validation packaging failed' }
    Copy-Item -LiteralPath $desktopArtifact -Destination (Join-Path $desktopPayload 'app/device-ops-server.jar')
    Copy-Item -LiteralPath $WinSwExecutable -Destination (Join-Path $desktopPayload 'SdpDacService.exe')
    Copy-Item -LiteralPath (Join-Path $desktopSource 'SdpDacService.xml') -Destination $desktopPayload
    Invoke-WebRequest 'https://raw.githubusercontent.com/winsw/winsw/v2.12.0/LICENSE.txt' -OutFile (Join-Path $desktopPayload 'WinSW-LICENSE.txt')
    Copy-Item -LiteralPath (Join-Path $desktopSource 'README.md') -Destination (Join-Path $desktopPayload 'README.md')
    Copy-Item -LiteralPath (Join-Path $taskWorkspace 'device-ops-platform/parser-releases/device-command-output-1.4.0') -Destination (Join-Path $desktopPayload 'parser-release') -Recurse
    $desktopModules = 'java.base,java.compiler,java.desktop,java.instrument,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql.rowset,jdk.charsets,jdk.crypto.ec,jdk.crypto.mscapi,jdk.jfr,jdk.localedata,jdk.management,jdk.naming.dns,jdk.net,jdk.security.auth,jdk.unsupported,jdk.zipfs'
    & (Join-Path $taskJavaHome 'bin/jlink.exe') --add-modules $desktopModules --strip-debug --no-header-files --no-man-pages --compress=zip-9 --output (Join-Path $desktopPayload 'runtime')
    if ($LASTEXITCODE -ne 0) { throw 'Java runtime linking failed' }

    # Harvest only generic payload. Service and protocol files have stable authored component GUIDs.
    $desktopHarvest = Join-Path $desktopOutput 'payload.wxs'
    & (Join-Path $WixDirectory 'heat.exe') dir $desktopPayload -nologo -cg RuntimePayload -dr INSTALLFOLDER -gg -sfrag -srd -sreg -scom -var var.Payload -out $desktopHarvest
    if ($LASTEXITCODE -ne 0) { throw 'Payload harvesting failed' }
    [xml]$desktopXml = Get-Content -LiteralPath $desktopHarvest -Raw
    $desktopNs = New-Object Xml.XmlNamespaceManager($desktopXml.NameTable)
    $desktopNs.AddNamespace('w', 'http://schemas.microsoft.com/wix/2006/wi')
    foreach ($desktopFile in @($desktopXml.SelectNodes('//w:Component/w:File', $desktopNs))) {
        if ([IO.Path]::GetFileName($desktopFile.Source) -in @('SdpDacService.exe', 'SdpDacService.xml', 'SdpDac.exe')) {
            $desktopComponent = $desktopFile.ParentNode
            $desktopReference = $desktopXml.SelectSingleNode("//w:ComponentRef[@Id='$($desktopComponent.Id)']", $desktopNs)
            if ($desktopReference) { [void]$desktopReference.ParentNode.RemoveChild($desktopReference) }
            [void]$desktopComponent.ParentNode.RemoveChild($desktopComponent)
        }
    }
    $desktopXml.Save($desktopHarvest)
    & (Join-Path $WixDirectory 'candle.exe') -nologo -arch x64 -ext WixUtilExtension -ext WixUIExtension "-dPayload=$desktopPayload" "-dVersion=$Version" "-dInstallerActions=$desktopActionsPackage" "-dDefaultServerUrl=$DefaultServerUrl" "-dDefaultWebOrigin=$DefaultWebOrigin" -out ($desktopOutput + '\') (Join-Path $desktopSource 'Package.wxs') $desktopHarvest
    if ($LASTEXITCODE -ne 0) { throw 'Installer source compilation failed' }
    $desktopMsi = Join-Path $desktopOutput "SDP-DAC-$Version-x64.msi"
    & (Join-Path $WixDirectory 'light.exe') -nologo -ext WixUtilExtension -ext WixUIExtension -cultures:zh-cn -out $desktopMsi (Join-Path $desktopOutput 'Package.wixobj') (Join-Path $desktopOutput 'payload.wixobj') *> (Join-Path $desktopOutput 'logs/installer-link.log')
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $desktopOutput 'logs/installer-link.log') -Tail 30; throw 'Installer linking or validation failed' }
    Copy-Item -LiteralPath $desktopDtf -Destination $desktopOutput
    & $desktopCompiler /nologo /optimize+ /platform:x64 /target:exe "/out:$(Join-Path $desktopOutput 'InstallerValidationTests.exe')" "/r:$desktopDtf" (Join-Path $desktopSource 'InstallerValidationTests.cs')
    if ($LASTEXITCODE -ne 0) { throw 'Installer regression test compilation failed' }
    & (Join-Path $desktopOutput 'InstallerValidationTests.exe') $desktopMsi *> (Join-Path $desktopOutput 'logs/installer-validation.log')
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $desktopOutput 'logs/installer-validation.log'); throw 'Installer regression tests failed' }
    Get-Content -LiteralPath (Join-Path $desktopOutput 'logs/installer-validation.log')
    & (Join-Path $WixDirectory 'dark.exe') -nologo -x (Join-Path $desktopOutput 'extracted') -o (Join-Path $desktopOutput 'verified-installer.wxs') $desktopMsi *> (Join-Path $desktopOutput 'logs/installer-extract.log')
    if ($LASTEXITCODE -ne 0) { throw 'Read-only installer extraction failed' }
    [xml]$desktopInstalledXml = Get-Content -LiteralPath (Join-Path $desktopOutput 'verified-installer.wxs') -Raw
    $desktopInstalledNs = New-Object Xml.XmlNamespaceManager($desktopInstalledXml.NameTable)
    $desktopInstalledNs.AddNamespace('w', 'http://schemas.microsoft.com/wix/2006/wi')
    [xml]$desktopAuthoredXml = Get-Content -LiteralPath (Join-Path $desktopSource 'Package.wxs') -Raw -Encoding UTF8
    $desktopAuthoredNs = New-Object Xml.XmlNamespaceManager($desktopAuthoredXml.NameTable)
    $desktopAuthoredNs.AddNamespace('w', 'http://schemas.microsoft.com/wix/2006/wi')
    $desktopPackagedFiles = @($desktopXml.SelectNodes('//w:File', $desktopNs)) + @($desktopAuthoredXml.SelectNodes('//w:File', $desktopAuthoredNs))
    foreach ($desktopFile in $desktopPackagedFiles) {
        $desktopOriginal = $desktopFile.Source.Replace('$(var.Payload)', $desktopPayload)
        $desktopExtracted = $desktopInstalledXml.SelectSingleNode("//w:File[@Id='$($desktopFile.Id)']", $desktopInstalledNs)
        if (!$desktopExtracted -or (Get-FileHash -LiteralPath $desktopOriginal).Hash -ne (Get-FileHash -LiteralPath $desktopExtracted.Source).Hash) {
            throw "Installer payload differs: $($desktopFile.Id)"
        }
    }
    $desktopFiles = @(Get-ChildItem -LiteralPath $desktopPayload -Recurse -File)
    $desktopFiles | ForEach-Object {
        [ordered]@{ path = $_.FullName.Substring($desktopPayload.Length + 1); bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    } | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $desktopOutput 'package-files.json') -Encoding utf8
    [ordered]@{
        version = $Version; installer = $desktopMsi; installerBytes = (Get-Item -LiteralPath $desktopMsi).Length
        payloadBytes = ($desktopFiles | Measure-Object -Property Length -Sum).Sum; payloadFiles = $desktopFiles.Count
        sourceManifest = $desktopManifest; runtimeProfile = 'desktop'; installedOrStarted = $false
        parserRelease = 'device-command-output-1.4.0'
        extractedFilesVerified = $desktopPackagedFiles.Count
        installerConfigurationValidation = 'passed-real-msi-session'
        customActionHost = 'WixToolset.Dtf 5.0.2'
        defaultServerUrl = $DefaultServerUrl; defaultWebOrigin = $DefaultWebOrigin
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $desktopOutput 'package-result.json') -Encoding utf8
    Get-Content -LiteralPath (Join-Path $desktopOutput 'package-result.json')
} finally { $desktopLock.Dispose() }
