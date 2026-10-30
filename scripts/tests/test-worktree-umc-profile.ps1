$ErrorActionPreference='Stop'
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'worktree-umc-profile.ps1')
Add-Type -AssemblyName System.Security
$taskTempRoot=[IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$taskDirectory=Join-Path $taskTempRoot ('npdms-umc-profile-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskDirectory | Out-Null
$taskProfilePath=Join-Path $taskDirectory 'profile.json'
$taskSecret='synthetic-test-only-'+[guid]::NewGuid().ToString('N')
$taskSecretBytes=[Text.Encoding]::UTF8.GetBytes($taskSecret)
$taskCipher=[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect($taskSecretBytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
[Array]::Clear($taskSecretBytes,0,$taskSecretBytes.Length)
$script:taskChecks=0
function Assert-UmcProfileTest {
    param([bool]$Condition,[string]$Message)
    if (!$Condition) { throw $Message }
    $script:taskChecks++
}
function Write-UmcTestProfile {
    param([hashtable]$Overrides=@{})
    $taskProfile=@{schemaVersion=1;baseUrl='https://umc.test/';username='synthetic-user';encryptedPassword=$taskCipher;nodeExecutable='node';timeoutSeconds=180;certificateSha256=('ab'*32)}
    foreach ($taskKey in $Overrides.Keys) { $taskProfile[$taskKey]=$Overrides[$taskKey] }
    $taskProfile | ConvertTo-Json | Set-Content -LiteralPath $taskProfilePath -Encoding UTF8
    Assert-UmcProfileTest (!(Get-Content -LiteralPath $taskProfilePath -Raw).Contains($taskSecret)) 'A synthetic plaintext password was written to the profile'
}
function Assert-UmcProfileRejected {
    param([hashtable]$Overrides)
    Write-UmcTestProfile $Overrides
    $taskRejected=$false
    $taskOutput=& {
        try { $null=Get-WorktreeUmcConfiguration -Path $taskProfilePath }
        catch {
            $script:taskRejection=$_.Exception.Message
            $script:taskWasRejected=$true
        }
    } *>&1 | Out-String
    $taskRejected=$script:taskWasRejected
    $script:taskWasRejected=$false
    Assert-UmcProfileTest $taskRejected 'An invalid protected profile was accepted'
    Assert-UmcProfileTest (!$taskOutput.Contains($taskSecret) -and !$script:taskRejection.Contains($taskSecret)) 'A validation error exposed the synthetic password'
    Assert-UmcProfileTest ($script:taskRejection -like '*no service changed*') 'Invalid profiles must fail before changing a service'
}
try {
    $taskMissingOutput=& { $script:taskMissing=Get-WorktreeUmcConfiguration -Path $taskProfilePath } *>&1 | Out-String
    Assert-UmcProfileTest ($null -eq $script:taskMissing) 'Missing optional profiles must not block unrelated backend capabilities'
    Assert-UmcProfileTest ($taskMissingOutput -like '*UMC is not configured*') 'Missing profiles need a clear warning'
    Write-UmcTestProfile
    $taskOutput=& { $script:taskLoaded=Get-WorktreeUmcConfiguration -Path $taskProfilePath } *>&1 | Out-String
    Assert-UmcProfileTest (!$taskOutput.Contains($taskSecret)) 'Successful validation printed the password'
    Assert-UmcProfileTest ($script:taskLoaded.Settings['pms.integration.umc.password'] -ceq $taskSecret) 'Current-user protected passwords did not decrypt in memory'
    Assert-UmcProfileTest ($script:taskLoaded.Settings['pms.integration.umc.base-url'] -eq 'https://umc.test/') 'The endpoint changed while loading'
    Assert-UmcProfileTest ($script:taskLoaded.Settings['pms.integration.umc.timeout-seconds'] -eq 180) 'The provider timeout was not retained'
    Assert-UmcProfileTest (Test-Path -LiteralPath $script:taskLoaded.Settings['pms.integration.umc.node-executable'] -PathType Leaf) 'The Node runtime was not resolved'
    Assert-UmcProfileRejected @{password=''}
    Assert-UmcProfileRejected @{baseUrl='http://umc.test/'}
    Assert-UmcProfileRejected @{baseUrl='https://user:credential@umc.test/'}
    Assert-UmcProfileRejected @{baseUrl='https://umc.test/other-path'}
    Assert-UmcProfileRejected @{encryptedPassword='broken-protected-value'}
    Assert-UmcProfileRejected @{encryptedPassword=[Convert]::ToBase64String([byte[]](1,2,3,4))}
    Assert-UmcProfileRejected @{nodeExecutable=(Join-Path $taskDirectory 'nonexistent-node.exe')}
    Assert-UmcProfileRejected @{timeoutSeconds=0}
    Assert-UmcProfileRejected @{certificateSha256='invalid-fingerprint'}
    Assert-UmcProfileRejected @{extraCaCertificatePath=(Join-Path $taskDirectory 'missing-ca.pem')}
    $taskBackend=[IO.File]::ReadAllText((Join-Path (Split-Path $PSScriptRoot -Parent) 'worktree-backend.ps1'))
    $taskParseErrors=$null
    $null=[Management.Automation.Language.Parser]::ParseInput($taskBackend,[ref]$null,[ref]$taskParseErrors)
    Assert-UmcProfileTest ($taskParseErrors.Count -eq 0) 'The controlled startup script has a syntax error'
    Write-Output "Protected UMC profile tests passed: $script:taskChecks checks; no real service or real credentials used."
} finally {
    $script:taskLoaded=$null
    $taskSecret=$null
    $taskResolved=[IO.Path]::GetFullPath($taskDirectory)
    if (!$taskResolved.StartsWith($taskTempRoot,[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($taskResolved) -notlike 'npdms-umc-profile-test-*') { throw 'Unexpected test cleanup path' }
    Remove-Item -LiteralPath $taskResolved -Recurse -Force
}
