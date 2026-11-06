param([Parameter(Mandatory = $true)][string]$Helper)
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This access check requires a real Windows user token' }
$exe = (Resolve-Path -LiteralPath $Helper).Path
$manifest = Get-Content -LiteralPath (Join-Path (Split-Path $exe -Parent) 'bridge-manifest.json') -Raw | ConvertFrom-Json
if ($manifest.ownershipBoundary -ne 'scm-process-listener' -or $manifest.format -ne 1 -or $manifest.bytes -ne (Get-Item -LiteralPath $exe).Length -or
    $manifest.sha256 -ne (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()) {
    throw 'Bridge integrity check failed'
}
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
$elevated = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
# Snapshot only. No service start, configuration write, ACL change or secret read.
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try { $result = & $exe snapshot 2>&1; $code = $LASTEXITCODE }
finally { $ErrorActionPreference = $previousPreference }
if ($code -ne 0) {
    $failure = if (($result | Out-String) -match 'DAC_WINDOWS_ACCESS_DENIED') { 'DAC_WINDOWS_ACCESS_DENIED' } else { 'DAC_WINDOWS_SERVICE_UNTRUSTED' }
    [ordered]@{ accessCheckPassed = $false; elevatedToken = $elevated; reason = $failure; serviceStarted = $false } | ConvertTo-Json
    exit 1
}
$snapshot = ($result | Out-String) | ConvertFrom-Json
if (!$snapshot.serviceState -or !$snapshot.serverUrl -or !$snapshot.webOrigin -or ($null -ne $snapshot.metadata -and $snapshot.ownershipVerified -ne $true)) { throw 'Invalid snapshot result' }
[ordered]@{ accessCheckPassed = $true; elevatedToken = $elevated; serviceState = $snapshot.serviceState;
    metadataPresent = $null -ne $snapshot.metadata; ownershipVerified = $snapshot.ownershipVerified; serviceStarted = $false; signedConnectionVerified = $false } | ConvertTo-Json
