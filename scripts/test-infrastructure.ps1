[CmdletBinding()]
param(
    [ValidateSet('start', 'status', 'reset')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'
$projectName = 'npdms-50eb-test'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$composeArguments = @('compose', '--project-name', $projectName)
$testEnvironment = @{
    NPDMS_DB_NAME    = 'npdms_test'
    NPDMS_MYSQL_PORT = '23316'
    NPDMS_REDIS_PORT = '26379'
}
$savedEnvironment = @{}

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed with exit code $LASTEXITCODE"
    }
}

function Invoke-TestMigration {
    Invoke-Docker ($composeArguments + @('create', '--no-recreate', 'migrate'))
    $logStart = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    Invoke-Docker ($composeArguments + @('start', 'migrate'))

    $containerId = (& docker @composeArguments ps --all --quiet migrate).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        throw 'Unable to resolve the fixed Flyway validation container'
    }

    $migrationExitCode = (& docker wait $containerId).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to wait for the fixed Flyway validation container'
    }
    Invoke-Docker @('logs', '--since', $logStart.ToString(), '--tail', '80', $containerId)
    if ($migrationExitCode -ne '0') {
        throw "Flyway validation container exited with code $migrationExitCode"
    }
}

try {
    foreach ($entry in $testEnvironment.GetEnumerator()) {
        $current = Get-Item -LiteralPath "Env:$($entry.Key)" -ErrorAction SilentlyContinue
        $savedEnvironment[$entry.Key] = if ($null -eq $current) { $null } else { $current.Value }
        Set-Item -LiteralPath "Env:$($entry.Key)" -Value $entry.Value
    }

    Push-Location $repositoryRoot
    try {
        switch ($Action) {
            'start' {
                Invoke-Docker ($composeArguments + @('up', '-d', '--wait', 'mysql', 'redis'))
                Invoke-TestMigration
                Invoke-Docker ($composeArguments + @('ps'))
            }
            'status' {
                Invoke-Docker ($composeArguments + @('ps', '--all'))
            }
            'reset' {
                Invoke-Docker ($composeArguments + @('up', '-d', '--wait', 'mysql', 'redis'))

                $mysqlReset = 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS npdms_test; CREATE DATABASE npdms_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"'
                Invoke-Docker ($composeArguments + @('exec', '-T', 'mysql', 'sh', '-c', $mysqlReset))

                $redisReset = 'exec redis-cli -a "$REDIS_PASSWORD" --no-auth-warning FLUSHALL'
                Invoke-Docker ($composeArguments + @('exec', '-T', 'redis', 'sh', '-c', $redisReset))

                Invoke-TestMigration
                Invoke-Docker ($composeArguments + @('ps', '--all'))
            }
        }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($entry in $savedEnvironment.GetEnumerator()) {
        if ($null -eq $entry.Value) {
            Remove-Item -LiteralPath "Env:$($entry.Key)" -ErrorAction SilentlyContinue
        } else {
            Set-Item -LiteralPath "Env:$($entry.Key)" -Value $entry.Value
        }
    }
}
