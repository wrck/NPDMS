# ISOLATED ACCEPTANCE ONLY. Never copy the V999 fixture location into production configuration.
# Run -PrepareOnly after clean to regenerate fixtures without starting Java or calling any API.
[CmdletBinding()]
param([switch]$PrepareOnly)

$ErrorActionPreference = 'Stop'
$platformRoot = Split-Path $PSScriptRoot -Parent
$outputRoot = Join-Path $platformRoot 'target/management-browser-fixture'
$seedRoot = Join-Path $outputRoot 'seed'
$jar = Join-Path $platformRoot 'device-ops-server/target/device-ops-server.jar'
New-Item -ItemType Directory -Force -Path $seedRoot | Out-Null

# Flyway alone owns ordering: packaged V1..V19 run before this isolated V999.
# No sql.init/defer ordering assumptions, production migration edits, attempts or outbox rows.
$content = 'show version'
$sha = [System.Security.Cryptography.SHA256]::Create()
try { $hash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($content)))).Replace('-', '').ToLowerInvariant() }
finally { $sha.Dispose() }
$sql = @"
-- Synthetic, terminal-only evidence. Host is RFC 5737 documentation space; never execute it.
INSERT INTO device_ops_script (id,namespace,script_key,source) VALUES (900001,'acceptance','acceptance-show','LOCAL_MANAGED');
INSERT INTO device_ops_script_version (id,script_id,version,content,sha256,parser_type) VALUES (900001,900001,'1.0.0','$content','$hash','NONE');
"@
$states = @('SUCCEEDED', 'FAILED', 'PARTIAL_SUCCESS')
for ($i = 0; $i -lt $states.Count; $i++) {
    $id = 'acceptance-history-' + ($i + 1)
    $targetId = 900001 + $i
    $status = $states[$i]
    $exitCode = if ($status -eq 'FAILED') { 1 } else { 0 }
    $sql += @"

INSERT INTO device_ops_collection (task_id,namespace,project_key,idempotency_key,activity_type,script_source,script_key,script_version,script_content,script_sha256,script_policy,parser_type,created_at)
VALUES ('$id','acceptance','acceptance-project','$id','INSPECTION','LOCAL_MANAGED','acceptance-show','1.0.0','$content','$hash','REGISTER_VERSION','NONE',TIMESTAMP '2026-09-08 10:0${i}:00');
INSERT INTO device_ops_collection_target (id,task_id,project_name,project_code,device_key,device_name,vendor,model,extensions_json,host,port,username,protocol,status,standard_output,standard_error,exit_code,outcome_message,output_truncated,parsed_facts_json)
VALUES ($targetId,'$id','Acceptance project','ACCEPTANCE','acceptance-device-$i','Synthetic device $i','SYNTHETIC','OFFLINE','{}','192.0.2.1',22,'fixture-only','SSH2','$status','Synthetic offline evidence; no device connection occurred.','',$exitCode,'Synthetic acceptance fixture',FALSE,'{}');
"@
}
$fixturePath = Join-Path $seedRoot 'V999__management_acceptance_fixture.sql'
[IO.File]::WriteAllText($fixturePath, $sql, (New-Object Text.UTF8Encoding($false)))
& (Join-Path $PSScriptRoot 'prepare-management-browser-fixture.ps1')
Write-Host "Prepared terminal collection/script fixture: $fixturePath"
if ($PrepareOnly) {
    Write-Host 'PrepareOnly: no Java process, database, device connection or API call was started.'
    return
}

if (!(Test-Path -LiteralPath $jar -PathType Leaf)) { throw "Built server JAR missing: $jar. Build separately, then rerun." }
$java = (Get-Command java -CommandType Application -ErrorAction Stop).Source
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 48182)
try { $listener.Start() }
catch { throw 'Port 127.0.0.1:48182 is unavailable. No existing process was stopped.' }
finally { $listener.Stop() }

$dbName = 'management_acceptance_' + [Guid]::NewGuid().ToString('N')
$seedLocation = 'filesystem:' + $seedRoot.Replace('\', '/')
$arguments = @(
    '-jar', $jar,
    '--spring.config.location=classpath:/application.yml',
    '--spring.profiles.active=acceptance-isolated',
    '--server.address=127.0.0.1', '--server.port=48182',
    "--spring.datasource.url=jdbc:h2:mem:${dbName};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    '--spring.datasource.driver-class-name=org.h2.Driver',
    '--spring.datasource.username=sa', '--spring.datasource.password=',
    '--spring.flyway.enabled=true', "--spring.flyway.locations=classpath:db/migration,$seedLocation",
    '--spring.sql.init.mode=never',
    '--device-ops.security.mode=local', '--device-ops.runtime.auth-mode=local',
    '--device-ops.callback.enabled=false', '--device-ops.schedule.enabled=false',
    '--device-ops.master-data.enabled=false', '--device-ops.telnet.enabled=false',
    '--device-ops.runtime.telnet-enabled=false'
)
$info = New-Object Diagnostics.ProcessStartInfo
$info.FileName = $java
$info.WorkingDirectory = $outputRoot
$info.UseShellExecute = $false
# All arguments are generated locally, quoted to support worktree paths containing spaces.
$info.Arguments = ($arguments | ForEach-Object { '"' + $_ + '"' }) -join ' '
# Do not inherit external DB/Flyway overrides, Spring JSON/imports, Java agents or real secrets.
# Environment is changed on the child only; the caller's environment is never mutated.
foreach ($name in @($info.EnvironmentVariables.Keys)) {
    if ($name -match '^(SPRING_|DEVICE_OPS_|OIDC_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|LOGGING_|SERVER_)') {
        $info.EnvironmentVariables.Remove($name)
    }
}
$keyBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($keyBytes) }
finally { $rng.Dispose() }
$info.EnvironmentVariables['DEVICE_OPS_CREDENTIAL_MASTER_KEY'] = [Convert]::ToBase64String($keyBytes)
[Array]::Clear($keyBytes, 0, $keyBytes.Length)
$process = New-Object Diagnostics.Process
$process.StartInfo = $info
$started = $false
try {
    Write-Host 'ISOLATED LOCAL AUTH: http://127.0.0.1:48182 ; in-memory H2, synthetic terminal history only.'
    Write-Host 'Do not execute collection commands against any device. Stop this script to end the acceptance server.'
    $started = $process.Start()
    if (!$started) { throw 'Java process did not start.' }
    # Block in this launcher, rather than detaching a server that outlives its task.
    while (!$process.WaitForExit(500)) { }
    if ($process.ExitCode -ne 0) { throw "Acceptance server exited with code $($process.ExitCode)." }
}
finally {
    if ($started -and !$process.HasExited) { $process.Kill(); $process.WaitForExit() }
    $info.EnvironmentVariables.Remove('DEVICE_OPS_CREDENTIAL_MASTER_KEY')
    $process.Dispose()
}
