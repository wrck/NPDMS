param(
    [ValidateSet('backend','dac')][string]$Target='backend',
    [string[]]$MavenArguments=@(),
    [switch]$ValidateOnly,
    [switch]$AllowOfflineRuntime
)
. (Join-Path $PSScriptRoot 'worktree-toolchain.ps1')
$taskLock = Enter-WorktreeBuildLock
$taskPreviousJava = $env:JAVA_HOME
try {
    if (!(Test-Path -LiteralPath "$taskJavaHome/bin/java.exe") -or !(Test-Path -LiteralPath $taskMaven)) { throw 'Pinned toolchain is unavailable' }
    $env:JAVA_HOME = $taskJavaHome
    Push-Location $taskWorkspace
    try {
        if ($ValidateOnly) { & $taskMaven --version; if ($LASTEXITCODE -ne 0) { throw 'Toolchain validation failed' }; return }
        foreach ($taskArgument in $MavenArguments) {
            if ($taskArgument -match 'maven.repo.local|^-pl$|^-f$|^-am$|^-amd$') { throw 'Repository and reactor scope cannot be overridden' }
        }
        $taskModule = if ($Target -eq 'backend') { 'yudao-server' } else { 'device-ops-server' }
        $taskReactor = if ($Target -eq 'backend') { $taskWorkspace } else { Join-Path $taskWorkspace 'device-ops-platform' }
        $taskBefore = & $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') source $taskWorkspace
        if ($LASTEXITCODE -ne 0) { throw 'Source inventory failed' }
        & $taskMaven -f "$taskReactor/pom.xml" -pl $taskModule -am clean package "-Dmaven.repo.local=$taskRepository" @MavenArguments
        if ($LASTEXITCODE -ne 0) { throw 'Build failed; no release produced' }
        $taskAfter = & $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') source $taskWorkspace
        if ($taskBefore -ne $taskAfter) { throw 'Source changed during build; artifact cannot be published' }
        $taskJar = Join-Path $taskReactor "$taskModule/target/$taskModule.jar"
        $taskFreezeArguments=@('freeze',$taskWorkspace,$Target,$taskJar,$taskAfter)
        if ($AllowOfflineRuntime) {
            if ($Target -ne 'backend') { throw 'Offline runtime freezing is only supported for the backend.' }
            $taskFreezeArguments+='--allow-offline-runtime'
        }
        & $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') @taskFreezeArguments
        if ($LASTEXITCODE -ne 0) { throw 'Release validation failed' }
    } finally { Pop-Location }
} finally { $env:JAVA_HOME=$taskPreviousJava; $taskLock.Dispose() }
