#Requires -Version 5.1
# ============================================================
# NPDMS 前后端本地开发服务启停脚本（Windows / PowerShell）。
#   以宿主机方式稳定启停：
#     - 后端：yudao-server.jar                 @ http://localhost:58080
#     - 前端：yudao-ui-admin-vue3（pnpm dev）   @ http://localhost:18081
#   每次启动把精确 PID 写入仓库根 .run/backend.pid 与 .run/frontend.pid，
#   stop/status 据此精确启停，不依赖外部进程查找。
#   密码句柄读取自仓库根 .env（被 .gitignore 忽略，不进仓库）。
#   MySQL / Redis 基础设施由 compose.yaml 权威启停；start 时若端口未
#   监听自动拉起并等待就绪后再启动应用。
#  用法：
#     .\scripts\service.ps1 start                # 启前后端（缺 jar 先构建）
#     .\scripts\service.ps1 start -Only backend  # 仅启后端
#     .\scripts\service.ps1 stop                 # 停前后端（含进程树）
#     .\scripts\service.ps1 status               # 查看 PID 存活与端口
#     .\scripts\service.ps1 restart              # 重启前后端
# ============================================================
param(
  [ValidateSet('start', 'stop', 'restart', 'status')]
  [string]$Command = 'start',
  [ValidateSet('backend', 'frontend', 'all')]
  [string]$Only = 'all'
)

$ErrorActionPreference = 'Stop'

# ---------- 路径与常量 ----------
$root   = Split-Path -Parent $PSScriptRoot          # 仓库根
$runDir = Join-Path $root '.run'
$logDir = Join-Path $runDir 'logs'
$pidBk  = Join-Path $runDir 'backend.pid'
$pidFr  = Join-Path $runDir 'frontend.pid'

# 端口约定（与 compose.yaml / development.md 一致）
$bkPort = 58080
$frPort = 18081

$jar    = Join-Path $root 'yudao-server\target\yudao-server.jar'
$feRoot = Join-Path $root 'yudao-ui\yudao-ui-admin-vue3'
$logBk  = Join-Path $logDir 'backend.log'
$logFr  = Join-Path $logDir 'frontend.log'
$logBkErr = Join-Path $logDir 'backend.err.log'
$logFrErr = Join-Path $logDir 'frontend.err.log'

# ---------- 读 .env（无 BOM，键=值） ----------
function Get-Env {
  $envFile = Join-Path $root '.env'
  if (-not (Test-Path $envFile)) {
    throw ".env 不存在：$envFile（请参考 .env.example 配置本地开发环境）"
  }
  $map = @{}
  foreach ($line in (Get-Content $envFile)) {
    if ($line -match '^\s*[^#][^=]*=.+') {
      $p = $line.TrimStart([char]0xFEFF) -split '=', 2
      $map[$p[0].Trim()] = $p[1].Trim().Trim('"')
    }
  }
  return $map
}

# ---------- PID 读写与存活 ----------
function Get-RecordedPid([string]$file) {
  if (Test-Path $file) {
    $id = (Get-Content $file -Raw).Trim()
    if ($id -match '^\d+$') { return [int]$id }
  }
  return $null
}
function Is-Alive([int]$id) {
  if (-not $id) { return $false }
  return $null -ne (Get-Process -Id $id -ErrorAction SilentlyContinue)
}
function Write-Pid([string]$file, [int]$id) {
  [System.IO.Directory]::CreateDirectory((Split-Path -Parent $file)) | Out-Null
  [System.IO.File]::WriteAllText($file, "$id", [System.Text.Encoding]::ASCII)
}
function Remove-Pid([string]$file) {
  if (Test-Path $file) { Remove-Item $file -Force }
}

# ---------- 端口快速探测（TCP） ----------
function Test-Port([int]$port) {
  $c = New-Object System.Net.Sockets.TcpClient
  try {
    $c.Connect('127.0.0.1', $port)
    return $true
  } catch { return $false }
  finally { $c.Dispose() }
}

# ---------- 按 PID 精确停止（含进程树，避免漏杀 vite/node 子进程） ----------
function Stop-ByPidFile([string]$file, [string]$label) {
  $id = Get-RecordedPid $file
  if (-not $id -or -not (Is-Alive $id)) {
    Remove-Pid $file
    Write-Host "  [$label] 未在运行（或 PID 已失效），跳过。"
    return
  }
  taskkill /PID $id /T /F | Out-Null
  Remove-Pid $file
  Write-Host "  [$label] 已停止（PID $id，含子进程）。"
}

# ---------- 基础设施就绪（compose 权威入口，缺则拉起并等待） ----------
function Ensure-Infra {
  $dbPort = [int]$(Get-Env)['NPDMS_MYSQL_PORT']
  $rdPort = [int]$(Get-Env)['NPDMS_REDIS_PORT']
  if (-not (Test-Port $dbPort) -or -not (Test-Port $rdPort)) {
    Write-Host '  [infra] MySQL/Redis 未就绪，调用 docker compose 拉起...'
    Push-Location $root
    try { docker compose up -d mysql redis | Out-Null } finally { Pop-Location }
  }
  $dbReady = $false; $rdReady = $false
  for ($i = 0; $i -lt 30; $i++) {
    if (-not $dbReady) { $dbReady = Test-Port $dbPort }
    if (-not $rdReady) { $rdReady = Test-Port $rdPort }
    if ($dbReady -and $rdReady) { break }
    Start-Sleep -Seconds 1
  }
  if (-not ($dbReady -and $rdReady)) {
    throw "基础设施未就绪：[mysql:$dbPort=$dbReady] [redis:$rdPort=$rdReady]。请检查 docker compose ps"
  }
  Write-Host "  [infra] MySQL($dbPort)/Redis($rdPort) 就绪。"
}

# ---------- 后端启动（缺 jar 先构建） ----------
function Start-Backend {
  if (Test-Port $bkPort -and (Is-Alive (Get-RecordedPid $pidBk))) {
    Write-Host '  [backend] 已在运行，跳过。'
    return
  }
  # 构建缺失的 jar
  if (-not (Test-Path $jar)) {
    Write-Host '  [backend] 未发现 jar，执行 mvn package ...'
    $javaHome = Get-Java25
    $env:JAVA_HOME = $javaHome
    Push-Location $root
    try {
      & 'mvn' -pl yudao-server -am package -DskipTests -q
      if ($LASTEXITCODE -ne 0) { throw 'mvn package 失败' }
    } finally { Pop-Location }
  }
  $env = Get-Env
  $java = Join-Path (Get-Java25) 'bin\java.exe'
  $dsUrl = "jdbc:mysql://127.0.0.1:$($env['NPDMS_MYSQL_PORT'])/$($env['NPDMS_DB_NAME'])?" +
    'useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' +
    '&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true'
  $args = @(
    '-jar', $jar,
    "--server.port=$bkPort",
    '--spring.profiles.active=local',
    "--spring.datasource.dynamic.datasource.master.url=$dsUrl",
    "--spring.datasource.dynamic.datasource.master.username=$($env['NPDMS_DB_USER'])",
    "--spring.datasource.dynamic.datasource.master.password=$($env['NPDMS_DB_PASSWORD'])",
    '--spring.data.redis.host=127.0.0.1',
    "--spring.data.redis.port=$($env['NPDMS_REDIS_PORT'])",
    "--spring.data.redis.password=$($env['NPDMS_REDIS_PASSWORD'])"
  )
  [System.IO.Directory]::CreateDirectory($logDir) | Out-Null
  $proc = Start-Process -FilePath $java -ArgumentList $args -RedirectStandardOutput $logBk `
      -RedirectStandardError $logBkErr -WindowStyle Hidden -PassThru
  Write-Pid $pidBk $proc.Id
  # 等待健康
  for ($i = 0; $i -lt 60 -and -not (Test-Port $bkPort); $i++) { Start-Sleep -Seconds 1 }
  if (-not (Test-Port $bkPort)) {
    Write-Host "  [backend] 启动超时，日志：$logBk"
  } else {
    Write-Host "  [backend] 已启动（PID $($proc.Id)） @ http://localhost:$bkPort"
  }
}

# ---------- 前端启动 ----------
function Start-Frontend {
  if (Test-Port $frPort -and (Is-Alive (Get-RecordedPid $pidFr))) {
    Write-Host '  [frontend] 已在运行，跳过。'
    return
  }
  [System.IO.Directory]::CreateDirectory($logDir) | Out-Null
  # 直接以 pnpm.cmd 启动（端口由 .env.env.local 决定=18081）。
  # pnpm/cmd shim -> node(vite) 处于同进程树，stop 时用 taskkill /T 整体回收。
  $proc = Start-Process -FilePath 'pnpm.cmd' -ArgumentList @('dev') `
      -WorkingDirectory $feRoot -RedirectStandardOutput $logFr -RedirectStandardError $logFrErr `
      -WindowStyle Hidden -PassThru
  Write-Pid $pidFr $proc.Id
  for ($i = 0; $i -lt 90 -and -not (Test-Port $frPort); $i++) { Start-Sleep -Seconds 1 }
  if (-not (Test-Port $frPort)) {
    Write-Host "  [frontend] 启动超时，日志：$logFr"
  } else {
    Write-Host "  [frontend] 已启动（PID $($proc.Id)） @ http://localhost:$frPort"
  }
}

# ---------- 探测 JDK（优先项目固定的 JDK25，避免误用 21+ 的旧 JAVA_HOME） ----------
function Get-Java25 {
  $cands = @('C:\Program Files\Java\jdk-25.0.1+8')
  if ($env:JAVA_HOME) { $cands += [System.Environment]::ExpandEnvironmentVariables($env:JAVA_HOME) }
  foreach ($c in $cands) {
    $j = Join-Path $c 'bin\java.exe'
    if (Test-Path $j) { return $c }
  }
  throw '未定位到 JDK 25，请设置 JAVA_HOME 指向 JDK 25'
}

# ---------- 调度 ----------
$want = $Only -in @('all', 'backend')
$wantF = $Only -in @('all', 'frontend')

switch ($Command) {
  'start' {
    Write-Host '== 启动 NPDMS 本地服务 =='
    if ($want -or $wantF) { Ensure-Infra }
    if ($want)  { Start-Backend }
    if ($wantF) { Start-Frontend }
    Write-Host '完成。status 查看详情，stop 停止。'
  }
  'stop' {
    Write-Host '== 停止 NPDMS 本地服务 =='
    if ($wantF) { Stop-ByPidFile $pidFr 'frontend' }
    if ($want)  { Stop-ByPidFile $pidBk 'backend' }
  }
  'restart' {
    Write-Host '== 重启 NPDMS 本地服务 =='
    if ($wantF) { Stop-ByPidFile $pidFr 'frontend' }
    if ($want)  { Stop-ByPidFile $pidBk 'backend' }
    Start-Sleep -Seconds 1
    if ($want -or $wantF) { Ensure-Infra }
    if ($want)  { Start-Backend }
    if ($wantF) { Start-Frontend }
  }
  'status' {
    Write-Host '== NPDMS 本地服务状态 =='
    foreach ($svc in @(@{n='backend'; f=$pidBk; p=$bkPort}, @{n='frontend'; f=$pidFr; p=$frPort})) {
      $id = Get-RecordedPid $svc.f
      $alive = (($id) -and (Is-Alive $id))
      $portOpen = Test-Port $svc.p
      $state = 'RUNNING'
      if (-not $id) { $state = 'STOPPED(no pid)' }
      elseif (-not $alive) { $state = 'STALE pid' }
      elseif (-not $portOpen) { $state = 'PORT-DOWN' }
      $idTxt = '-'
      if ($id) { $idTxt = [string]$id }
      Write-Host ('  ' + $svc.n.PadRight(10) + $state.PadRight(16) + 'pid=' + $idTxt.PadRight(6) + 'port=' + $svc.p)
    }
  }
}