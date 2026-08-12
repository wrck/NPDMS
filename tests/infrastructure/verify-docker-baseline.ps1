[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$composeFile = Join-Path $repoRoot "compose.yaml"
$migrationFile = Join-Path $repoRoot "sql\migrations\V1__yudao_platform.sql"
$sourceSql = Join-Path $repoRoot "sql\mysql\ruoyi-vue-pro.sql"
$envExample = Join-Path $repoRoot ".env.example"
$frontendEnv = Join-Path $repoRoot "yudao-ui\yudao-ui-admin-vue3\.env"
$applicationConfig = Join-Path $repoRoot "yudao-server\src\main\resources\application.yaml"
$dockerApplicationConfig = Join-Path $repoRoot "yudao-server\src\main\resources\application-docker.yaml"

foreach ($required in @(
    $composeFile,
    $migrationFile,
    $sourceSql,
    $envExample,
    $frontendEnv,
    $applicationConfig,
    $dockerApplicationConfig
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required baseline file is missing: $required"
    }
}

# Docker Compose 仅承载基础设施（MySQL、Redis、Flyway），前端与后端禁止运行在 Docker 中.
$compose = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($composeFile))
if ([string]::IsNullOrEmpty($compose)) {
    throw "compose.yaml read as empty from: $composeFile"
}
if ($compose -notmatch '(?m)^name:\s*npdms\s*$') {
    throw "Compose project name must be npdms."
}
if ($compose -notmatch [regex]::Escape('${NPDMS_DB_NAME:-npdms}')) {
    throw "Compose default database must be npdms and use NPDMS_DB_NAME."
}
if ($compose -match '\$\{PMS_') {
    throw "Compose must not reference legacy PMS_* environment variables."
}
foreach ($expected in @(
    "mysql:8.4",
    "redis:7.4-alpine",
    "flyway/flyway:11.10.5-alpine"
)) {
    if ($compose -notmatch [regex]::Escape($expected)) {
        throw "Pinned infrastructure image is not declared in compose.yaml: $expected"
    }
}
if ($compose -match '(?m)^\s{2}server:') {
    throw "compose.yaml must not define a 'server' service; backend must run on the host."
}
if ($compose -match '(?m)^\s{2}frontend:') {
    throw "compose.yaml must not define a 'frontend' service; frontend must run on the host."
}

# 前端在宿主机运行，必须代理到宿主机后端 58080，而非 Docker 内部服务名.
$frontendEnvLines = Get-Content -LiteralPath $frontendEnv
if ($frontendEnvLines -notcontains "VITE_API_URL=/admin-api") {
    throw "Frontend API path must remain /admin-api."
}
if ($frontendEnvLines -notcontains "VITE_PROXY_TARGET=http://localhost:58080") {
    throw "Frontend proxy must target the host backend at http://localhost:58080."
}
if ($frontendEnvLines -notcontains "VITE_APP_TENANT_ENABLE=false") {
    throw "Frontend must explicitly use the V1 single-tenant mode."
}
if ($frontendEnvLines -notcontains "VITE_PORT=18081") {
    throw "Frontend dev server must listen on the pinned port 18081."
}

$applicationContent = Get-Content -Raw -LiteralPath $applicationConfig
if ($applicationContent -notmatch "(?ms)^  tenant:\s*#.*?\r?\n^    enable:\s*false\b") {
    throw "The default backend tenant mode must be disabled for V1."
}
$dockerApplicationContent = Get-Content -Raw -LiteralPath $dockerApplicationConfig
if ($dockerApplicationContent -match "(?ms)^  tenant:\s*(?:#.*)?\r?\n^    enable:") {
    throw "Docker profile must not override the global tenant mode."
}
foreach ($disabledIntegration in @(
    "doubao",
    "hunyuan",
    "siliconflow",
    "xinghuo",
    "baichuan",
    "midjourney",
    "suno",
    "web-search"
)) {
    $pattern = "(?ms)^    $([regex]::Escape($disabledIntegration)):(?:\s*#.*)?\r?\n^      enable:\s*false\b"
    if ($applicationContent -notmatch $pattern) {
        throw "Unused integration must remain disabled: $disabledIntegration"
    }
}
if ($applicationContent -notmatch "status-sync-to-wxa-enable:\s*false\b") {
    throw "Trade-to-WeChat miniapp synchronization must remain disabled."
}

$sensitiveNames = @(
    "NPDMS_DB_PASSWORD",
    "NPDMS_MYSQL_ROOT_PASSWORD",
    "NPDMS_REDIS_PASSWORD",
    "NPDMS_MYBATIS_ENCRYPTOR_PASSWORD"
)
$exampleLines = Get-Content -LiteralPath $envExample
foreach ($name in $sensitiveNames) {
    $line = $exampleLines | Where-Object { $_ -match "^$name=" }
    if ($null -eq $line -or $line -ne "$name=") {
        throw "$name must be present and blank in .env.example"
    }
}

$sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourceSql).Hash
$migrationHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $migrationFile).Hash
if ($sourceHash -ne $migrationHash) {
    throw "V1 migration must remain byte-identical to the locked official platform SQL."
}

$excludedPomPathPattern = '[\\/](target|\._codex_work)[\\/]'
foreach ($excludedPath in @(
    "C:\repo\._codex_work\upstream\pom.xml",
    "/repo/._codex_work/upstream/pom.xml"
)) {
    if ($excludedPath -notmatch $excludedPomPathPattern) {
        throw "POM exclusion pattern must support Windows and Unix separators: $excludedPath"
    }
}
$pomFiles = Get-ChildItem -Path $repoRoot -Recurse -Filter pom.xml |
    Where-Object { $_.FullName -notmatch $excludedPomPathPattern }
if ($pomFiles | Select-String -Pattern 'flyway' -SimpleMatch) {
    throw "Flyway must not be added as an application runtime/build dependency."
}

Push-Location $repoRoot
try {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    & docker compose --env-file .env.example config --quiet 2>$null
    $exampleExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorPreference
    if ($exampleExitCode -eq 0) {
        throw "Compose must reject the credential-free example file."
    }
    & docker compose --env-file .env config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Compose configuration validation failed."
    }
} finally {
    Pop-Location
}

Write-Output "Docker baseline static verification passed."
