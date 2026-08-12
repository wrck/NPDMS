[CmdletBinding()]
param(
    [string]$Path = (Join-Path $PSScriptRoot "..\..\.env")
)

$resolvedPath = [System.IO.Path]::GetFullPath($Path)
if (Test-Path -LiteralPath $resolvedPath) {
    throw "Refusing to overwrite existing environment file: $resolvedPath"
}

function New-HexSecret {
    param([int]$ByteCount = 24)
    $bytes = New-Object byte[] $ByteCount
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

$content = @(
    "NPDMS_DB_NAME=npdms"
    "NPDMS_DB_USER=npdms_app"
    "NPDMS_DB_PASSWORD=$(New-HexSecret)"
    "NPDMS_MYSQL_ROOT_PASSWORD=$(New-HexSecret)"
    "NPDMS_REDIS_PASSWORD=$(New-HexSecret)"
    "NPDMS_MYBATIS_ENCRYPTOR_PASSWORD=$(New-HexSecret)"
    "NPDMS_MYSQL_PORT=13306"
    "NPDMS_REDIS_PORT=16379"
    "# 前后端端口已在 compose.yaml 中固化为 58080/18081，禁止通过 .env 修改"
)

[System.IO.File]::WriteAllLines($resolvedPath, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "Created local environment file: $resolvedPath"
