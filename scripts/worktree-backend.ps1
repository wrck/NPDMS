param([Parameter(Mandatory=$true)][string]$Manifest,[switch]$ApprovedRestart,[switch]$ValidateOnly,[switch]$StartIfOffline)
. (Join-Path $PSScriptRoot 'worktree-toolchain.ps1')
$taskLock=Enter-WorktreeBuildLock
try {
    $taskResult = & $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') verify $taskWorkspace $Manifest
    if ($LASTEXITCODE -ne 0) { throw 'Release verification failed; no service changed' }
    $taskArtifact = $taskResult[-1]
    $taskPidFile=Join-Path $taskWorkspace '.run/backend-domain-test.pid'
    $taskPid=(Get-Content -LiteralPath $taskPidFile -Raw).Trim()
    if ($taskPid -notmatch '^\d+$') { throw 'Invalid backend PID' }
    $taskProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$taskPid"
    $taskRootPrefix=[IO.Path]::GetFullPath((Join-Path $taskWorkspace '.run'))+[IO.Path]::DirectorySeparatorChar
    $taskManifestData=Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json
    $taskOffline = !$taskProcess
    if ($taskOffline) {
        if (!$StartIfOffline) { throw 'Backend is offline; use -StartIfOffline after explicit approval.' }
        if (@(netstat -ano -p tcp | Select-String '^\s*TCP\s+.*:59191\s+.*LISTENING').Count -gt 0) { throw 'Port 59191 is occupied by an untracked process.' }
    } else {
        if ($taskProcess.CommandLine -notmatch '--server.port=59191(?:\s|$)') { throw 'Backend listener identity changed' }
        $taskMatch=[regex]::Match($taskProcess.CommandLine,'-jar\s+(?:"([^"]+)"|(\S+))')
        $taskCurrentJar=if ($taskMatch.Groups[1].Success) { $taskMatch.Groups[1].Value } else { $taskMatch.Groups[2].Value }
        if (![IO.Path]::GetFullPath($taskCurrentJar).StartsWith($taskRootPrefix,[StringComparison]::OrdinalIgnoreCase)) { throw 'Running artifact is outside this worktree runtime' }
        if ((Get-FileHash -LiteralPath $taskCurrentJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskManifestData.runningArtifactSha256) { throw 'Another deployment changed the running artifact after this release was frozen; prepare against the latest runtime.' }
        & $taskPython (Join-Path $taskWorkspace '.run/dac-host/verify-deployed-repairs.py') backend $taskCurrentJar
        if ($LASTEXITCODE -ne 0) { throw 'Running repairs changed; review before deployment' }
    }
    if ($ValidateOnly) { Write-Output 'Frozen release and pinned local,dac profile validated; no service changed.'; return }
    if (!$ApprovedRestart) { throw 'Explicit per-deployment restart approval is required' }
    . (Join-Path $taskWorkspace '.run/dac-host/environment.ps1')
    $taskSettings=@{}
    foreach ($taskLine in Get-Content (Join-Path $taskWorkspace '.env')) {
        if ($taskLine -match '^\s*([^#=]+)=(.*)$') { $taskSettings[$matches[1].Trim()]=$matches[2].Trim().Trim('"') }
    }
    $taskDestination=Join-Path $taskWorkspace '.run/dac-route-server.jar'
    if ($taskOffline) {
        $taskDestination=$taskArtifact
    } else {
        $taskBackupDirectory=Join-Path $taskWorkspace ('.run/build/backups/'+[guid]::NewGuid().ToString())
        New-Item -ItemType Directory -Path $taskBackupDirectory | Out-Null
        Copy-Item -LiteralPath $taskCurrentJar -Destination (Join-Path $taskBackupDirectory 'application.jar')
        Stop-Process -Id $taskProcess.ProcessId
        Wait-Process -Id $taskProcess.ProcessId -Timeout 30 -ErrorAction SilentlyContinue
        Copy-Item -LiteralPath $taskArtifact -Destination $taskDestination -Force
    }
    $env:NPDMS_MYBATIS_ENCRYPTOR_PASSWORD=$taskSettings['NPDMS_MYBATIS_ENCRYPTOR_PASSWORD']
    $env:SPRING_APPLICATION_JSON=(@{
        'spring.datasource.dynamic.datasource.master.url'='jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true'
        'spring.datasource.dynamic.datasource.master.username'=$taskSettings['NPDMS_DB_USER']
        'spring.datasource.dynamic.datasource.master.password'=$taskSettings['NPDMS_DB_PASSWORD']
        'spring.data.redis.host'='127.0.0.1';'spring.data.redis.port'=24379
        'spring.data.redis.password'=$taskSettings['NPDMS_REDIS_PASSWORD']
    } | ConvertTo-Json -Compress)
    $taskStarted=Start-Process -FilePath "$taskJavaHome/bin/java.exe" -ArgumentList @('-jar',$taskDestination,'--server.port=59191','--spring.profiles.active=local,dac') -WorkingDirectory $taskWorkspace -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskWorkspace '.run/logs/satisfaction-manual-backend.log') -RedirectStandardError (Join-Path $taskWorkspace '.run/logs/satisfaction-manual-backend.err.log')
    $taskStarted.Id | Set-Content -LiteralPath $taskPidFile
    & $taskPython (Join-Path $taskWorkspace '.run/dac-host/check-ready.py') backend
    if ($LASTEXITCODE -ne 0) { throw 'Backend readiness failed.' }
    if ($taskOffline) { Write-Output "Backend started from frozen local,dac artifact; PID $($taskStarted.Id)." }
    else { Write-Output "Backend deployed with local,dac; PID $($taskStarted.Id)." }
} finally {
    Remove-Item Env:SPRING_APPLICATION_JSON -ErrorAction SilentlyContinue
    Remove-Item Env:NPDMS_DEVICE_OPS_BEARER_TOKEN -ErrorAction SilentlyContinue
    $taskLock.Dispose()
}
