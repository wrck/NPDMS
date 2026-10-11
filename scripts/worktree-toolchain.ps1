$ErrorActionPreference = 'Stop'
$taskWorkspace = Split-Path $PSScriptRoot -Parent
$taskJavaHome = 'C:/Program Files/Java/jdk-25.0.1+8'
$taskMaven = 'E:/apache-maven-3.8.6/bin/mvn.cmd'
$taskPython = 'C:/Users/user/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$taskRepository = Join-Path $taskWorkspace '.run/build/m2'
# Exclusive file lock covers builds, frozen releases and deployment; released on process exit.
function Enter-WorktreeBuildLock {
    $taskLockDirectory = Join-Path $taskWorkspace '.run/build'
    New-Item -ItemType Directory -Path $taskLockDirectory -Force | Out-Null
    try {
        return [IO.File]::Open((Join-Path $taskLockDirectory 'worktree.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch { throw 'This worktree already has a build or deployment in progress; no action performed.' }
}
