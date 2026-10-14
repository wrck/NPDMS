param([Parameter(Mandatory=$true)][string]$Manifest,[switch]$ApprovedStart,[switch]$ValidateOnly,[switch]$StartGatewayIfOffline)
. (Join-Path $PSScriptRoot 'worktree-toolchain.ps1')
$taskLock=Enter-WorktreeBuildLock
try {
    $taskResult=& $taskPython (Join-Path $PSScriptRoot 'worktree-artifact.py') verify-dac $taskWorkspace $Manifest
    if ($LASTEXITCODE -ne 0) { throw 'DAC release validation failed' }
    $taskArtifact=$taskResult[-1]
    $taskSocket=New-Object Net.Sockets.TcpClient
    try { $taskSocket.Connect('10.210.0.11',48182); throw 'DAC port is already occupied; existing service retained' }
    catch [Net.Sockets.SocketException] { }
    finally { $taskSocket.Dispose() }
    $taskGatewaySocket=New-Object Net.Sockets.TcpClient
    try { $taskGatewaySocket.Connect('10.210.0.11',59192); $taskGatewayOnline=$true }
    catch [Net.Sockets.SocketException] { $taskGatewayOnline=$false }
    finally { $taskGatewaySocket.Dispose() }
    if (!$taskGatewayOnline -and !$StartGatewayIfOffline) { throw 'Callback gateway is offline; use -StartGatewayIfOffline after explicit approval.' }
    $taskActive=Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*device-ops-server*.jar*' -and $_.CommandLine -like '*npdms*' }
    if ($taskActive) { throw 'An existing DAC process is present; review it instead of starting a second instance' }
    if ($ValidateOnly) { Write-Output 'DAC frozen release and inactive listener validated; no service started.'; return }
    if (!$ApprovedStart) { throw 'Explicit DAC start approval is required' }
    . (Join-Path $taskWorkspace '.run/dac-host/environment.ps1')
    if (!$taskGatewayOnline) {
        $taskGateway=Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @((Join-Path $taskWorkspace '.run/dac-host/callback-gateway.cjs')) -WorkingDirectory (Join-Path $taskWorkspace '.run/dac-host') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskWorkspace '.run/dac-host/gateway.log') -RedirectStandardError (Join-Path $taskWorkspace '.run/dac-host/gateway.err.log')
        $taskGateway.Id | Set-Content (Join-Path $taskWorkspace '.run/dac-host/gateway.pid')
    }
    $env:SPRING_APPLICATION_JSON=(@{
        'server.address'='10.210.0.11';'server.ssl.enabled'=$true
        'server.ssl.key-store'=(Join-Path $taskWorkspace '.run/dac-host/tls.p12')
        'server.ssl.key-store-password'=$env:DAC_STORE_PASSWORD
        'server.ssl.key-store-type'='PKCS12'
        'device-ops.parser.automatic-parsing-enabled'=$true
    } | ConvertTo-Json -Compress)
    $taskStarted=Start-Process -FilePath "$taskJavaHome/bin/java.exe" -ArgumentList @('-Xmx512m','-jar',$taskArtifact,'--spring.profiles.active=npdms') -WorkingDirectory (Join-Path $taskWorkspace '.run/dac-host') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskWorkspace '.run/dac-host/dac.log') -RedirectStandardError (Join-Path $taskWorkspace '.run/dac-host/dac.err.log')
    $taskStarted.Id | Set-Content (Join-Path $taskWorkspace '.run/dac-host/dac.pid')
    & $taskPython (Join-Path $taskWorkspace '.run/dac-host/check-ready.py') dac
    if ($LASTEXITCODE -ne 0) { throw 'DAC readiness failed' }
    if ($taskGatewayOnline) { Write-Output "DAC started with immutable verified artifact; PID $($taskStarted.Id). Callback gateway retained." }
    else { Write-Output "DAC started with immutable verified artifact; PID $($taskStarted.Id). Callback gateway PID $($taskGateway.Id)." }
} finally {
    Remove-Item Env:SPRING_APPLICATION_JSON -ErrorAction SilentlyContinue
    Remove-Item Env:NPDMS_DEVICE_OPS_BEARER_TOKEN -ErrorAction SilentlyContinue
    $taskLock.Dispose()
}
