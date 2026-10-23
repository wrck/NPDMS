param([switch]$ApprovedRestart)

$ErrorActionPreference = 'Stop'
$taskWorkspace = Split-Path $PSScriptRoot -Parent
$taskPidFile = Join-Path $taskWorkspace '.run/frontend-domain-test.pid'
$taskFrontendRoot = Join-Path $taskWorkspace 'yudao-ui/yudao-ui-admin-vue3'
$taskPort = 19191

function Test-TaskPort([int]$Port) {
    $socket = New-Object Net.Sockets.TcpClient
    try { $socket.Connect('127.0.0.1', $Port); return $true }
    catch { return $false }
    finally { $socket.Dispose() }
}

$taskProcess = $null
if (Test-Path -LiteralPath $taskPidFile) {
    $taskPid = (Get-Content -LiteralPath $taskPidFile -Raw).Trim()
    if ($taskPid -match '^\d+$') { $taskProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$taskPid" }
}

if ($taskProcess) {
    if ($taskProcess.CommandLine -notlike "*$taskFrontendRoot*" -or $taskProcess.CommandLine -notlike '*19191*') {
        throw 'Frontend PID identity changed; no process stopped.'
    }
    if (!$ApprovedRestart) { throw 'Frontend is already running; explicit restart approval is required.' }
    Stop-Process -Id $taskProcess.ProcessId
    Wait-Process -Id $taskProcess.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
}

if (Test-TaskPort $taskPort) { throw 'Port 19191 is occupied by an untracked process.' }

$env:VITE_BASE_URL = 'http://10.210.0.11:59191'
$env:VITE_API_URL = '/admin-api'
$env:VITE_OPEN = 'false'
$taskVite = Join-Path $taskFrontendRoot 'node_modules/vite/bin/vite.js'
$taskNode = (Get-Command node.exe -ErrorAction Stop).Source
$taskStarted = Start-Process -FilePath $taskNode -ArgumentList @($taskVite, '--mode', 'env.local', '--host', '0.0.0.0', '--port', "$taskPort", '--strictPort') -WorkingDirectory $taskFrontendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskWorkspace '.run/dac-host/frontend.log') -RedirectStandardError (Join-Path $taskWorkspace '.run/dac-host/frontend.err.log')
$taskStarted.Id | Set-Content -LiteralPath $taskPidFile

for ($taskAttempt = 0; $taskAttempt -lt 30 -and -not (Test-TaskPort $taskPort); $taskAttempt++) { Start-Sleep -Seconds 1 }
if (!(Test-TaskPort $taskPort)) { throw 'Frontend start timeout; inspect .run/dac-host/frontend.err.log.' }
Write-Output "Frontend started at http://10.210.0.11:$taskPort; PID $($taskStarted.Id)."
