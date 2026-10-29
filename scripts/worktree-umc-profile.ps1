# Optional local profile. Secrets are decrypted only in memory for the child process.
function Get-WorktreeUmcConfiguration {
    param([Parameter(Mandatory=$true)][string]$Path)
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        Write-Warning 'UMC is not configured: no protected local UMC profile was found. Other backend capabilities remain available.'
        return $null
    }
    $taskUmcPasswordBytes = $null
    try {
        $taskUmcProfile = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        if ($taskUmcProfile.schemaVersion -ne 1 -or $taskUmcProfile.PSObject.Properties.Name -contains 'password') { throw 'Invalid profile schema' }
        $taskUmcUrl = [Uri]$taskUmcProfile.baseUrl
        if (!$taskUmcUrl.IsAbsoluteUri -or $taskUmcUrl.Scheme -ne 'https' -or $taskUmcUrl.UserInfo -or $taskUmcUrl.Query -or $taskUmcUrl.Fragment -or $taskUmcUrl.AbsolutePath -ne '/') { throw 'Invalid UMC endpoint' }
        if ([string]::IsNullOrWhiteSpace($taskUmcProfile.username) -or [string]::IsNullOrWhiteSpace($taskUmcProfile.encryptedPassword)) { throw 'Missing protected credential' }
        $taskUmcNode = if ([string]::IsNullOrWhiteSpace($taskUmcProfile.nodeExecutable)) { 'node' } else { $taskUmcProfile.nodeExecutable }
        $taskUmcNodeCommand = Get-Command -Name $taskUmcNode -CommandType Application -ErrorAction Stop | Select-Object -First 1
        $taskUmcNodeVersion = & $taskUmcNodeCommand.Source --version 2>$null
        if ($LASTEXITCODE -ne 0 -or $taskUmcNodeVersion -notmatch '^v(\d+)\.' -or [int]$matches[1] -lt 24) { throw 'Node 24 or newer is required' }
        $taskUmcTimeout = if ($null -eq $taskUmcProfile.timeoutSeconds) { 180 } else { [int]$taskUmcProfile.timeoutSeconds }
        if ($taskUmcTimeout -lt 1 -or $taskUmcTimeout -gt 1800) { throw 'Invalid UMC timeout' }
        $taskUmcCertificate = [string]$taskUmcProfile.certificateSha256
        if ($taskUmcCertificate -and $taskUmcCertificate.Replace(':','') -notmatch '^[0-9a-fA-F]{64}$') { throw 'Invalid UMC certificate fingerprint' }
        $taskUmcCa = [string]$taskUmcProfile.extraCaCertificatePath
        if ($taskUmcCa) {
            if (!(Test-Path -LiteralPath $taskUmcCa -PathType Leaf)) { throw 'UMC trusted CA file is missing' }
            $taskUmcCa = (Resolve-Path -LiteralPath $taskUmcCa -ErrorAction Stop).Path
        }
        Add-Type -AssemblyName System.Security
        $taskUmcPasswordBytes = [Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String($taskUmcProfile.encryptedPassword), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        $taskUmcPassword = [Text.Encoding]::UTF8.GetString($taskUmcPasswordBytes)
        if ([string]::IsNullOrWhiteSpace($taskUmcPassword)) { throw 'Empty protected credential' }
        return [pscustomobject]@{
            Settings = @{
                'pms.integration.umc.base-url' = $taskUmcUrl.AbsoluteUri
                'pms.integration.umc.username' = [string]$taskUmcProfile.username
                'pms.integration.umc.password' = $taskUmcPassword
                'pms.integration.umc.node-executable' = $taskUmcNodeCommand.Source
                'pms.integration.umc.timeout-seconds' = $taskUmcTimeout
                'pms.integration.umc.certificate-sha256' = $taskUmcCertificate
            }
            ExtraCaCertificatePath = $taskUmcCa
        }
    } catch {
        throw 'Protected UMC profile could not be loaded or validated; no service changed. Check its schema, endpoint, runtime, certificate and current-user encryption.'
    } finally {
        if ($taskUmcPasswordBytes) { [Array]::Clear($taskUmcPasswordBytes, 0, $taskUmcPasswordBytes.Length) }
    }
}
