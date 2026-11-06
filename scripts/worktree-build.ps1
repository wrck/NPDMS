param(
    [ValidateSet('backend','dac')][string]$Target='backend',
    [string[]]$MavenArguments=@(),
    [string]$BuildOutputRoot='',
    [switch]$ValidateOnly,
    [switch]$AllowOfflineRuntime
)
. (Join-Path $PSScriptRoot 'worktree-toolchain.ps1')
$taskLock = Enter-WorktreeBuildLock
$taskPreviousJava = $env:JAVA_HOME
try {
    $taskJavaExecutable = Join-Path $taskJavaHome ('bin/' + $(if ($IsLinux -or $IsMacOS) { 'java' } else { 'java.exe' }))
    if (!(Test-Path -LiteralPath $taskJavaExecutable) -or !(Test-Path -LiteralPath $taskMaven) -or !(Test-Path -LiteralPath $taskPython)) { throw 'Pinned toolchain is unavailable' }
    $taskJavaVersion = (& $taskJavaExecutable --version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $taskJavaVersion -notmatch '25\.0\.1\+8') { throw 'Pinned JDK 25.0.1+8 is required' }
    $env:JAVA_HOME = $taskJavaHome
    $taskMavenVersion = (& $taskMaven --version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $taskMavenVersion -notmatch 'Apache Maven 3\.8\.6\b') { throw 'Pinned Maven 3.8.6 is required' }
    Push-Location $taskWorkspace
    try {
        if ($ValidateOnly) { & $taskMaven --version; if ($LASTEXITCODE -ne 0) { throw 'Toolchain validation failed' }; return }
        foreach ($taskArgument in $MavenArguments) {
            if ($taskArgument -match 'maven.repo.local|npdms.build.root|^-pl$|^-f$|^-am$|^-amd$') { throw 'Repository, reactor scope and output root must use the controlled parameters' }
        }
        $taskModule = if ($Target -eq 'backend') { 'yudao-server' } else { 'device-ops-server' }
        $taskReactor = if ($Target -eq 'backend') { $taskWorkspace } else { Join-Path $taskWorkspace 'device-ops-platform' }
        $taskOutputArguments=@()
        $taskOutputRoot=$null
        if ($BuildOutputRoot) {
            if (![IO.Path]::IsPathFullyQualified($BuildOutputRoot)) { throw 'BuildOutputRoot must be an absolute directory' }
            $taskOutputRoot=[IO.Path]::GetFullPath($BuildOutputRoot)
            $taskTargetOutput=Join-Path $taskOutputRoot $Target
            $taskOutputArguments=@("-Dnpdms.build.root=$taskTargetOutput")
        }
        $taskBefore = & $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') source $taskWorkspace $Target
        if ($LASTEXITCODE -ne 0) { throw 'Source inventory failed' }
        & $taskMaven -f "$taskReactor/pom.xml" -pl $taskModule -am clean package "-Dmaven.repo.local=$taskRepository" @taskOutputArguments @MavenArguments
        if ($LASTEXITCODE -ne 0) { throw 'Build failed; no release produced' }
        $taskAfter = & $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') source $taskWorkspace $Target
        if ($LASTEXITCODE -ne 0) { throw 'Source inventory failed; no release produced' }
        if ($taskBefore -ne $taskAfter) { throw 'Source changed during build; artifact cannot be published' }
        $taskJar = Join-Path $taskReactor "$taskModule/target/$taskModule.jar"
        if ($taskOutputRoot) { $taskJar=Join-Path $taskTargetOutput "$taskModule/$taskModule.jar" }
        $taskFreezeArguments=@('freeze',$taskWorkspace,$Target,$taskJar,$taskAfter)
        if ($taskOutputRoot) { $taskFreezeArguments+=@('--release-root',(Join-Path $taskOutputRoot 'releases')) }
        if ($AllowOfflineRuntime) {
            if ($Target -ne 'backend') { throw 'Offline runtime freezing is only supported for the backend.' }
            $taskFreezeArguments+='--allow-offline-runtime'
        }
        & $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') @taskFreezeArguments
        if ($LASTEXITCODE -ne 0) { throw 'Release validation failed' }
    } finally { Pop-Location }
} finally { $env:JAVA_HOME=$taskPreviousJava; $taskLock.Dispose() }
