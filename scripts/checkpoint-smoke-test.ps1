$ErrorActionPreference = 'Continue'
$base = if ([string]::IsNullOrWhiteSpace($env:NPDMS_E2E_BASE_URL)) { 'http://localhost:58080' } else { $env:NPDMS_E2E_BASE_URL }
$api = "$base/admin-api"
$username = $env:NPDMS_E2E_USERNAME
$password = $env:NPDMS_E2E_PASSWORD
if ([string]::IsNullOrWhiteSpace($username) -or [string]::IsNullOrWhiteSpace($password)) {
    throw 'NPDMS_E2E_USERNAME and NPDMS_E2E_PASSWORD are required'
}

# 使用时间戳后缀避免与历史数据冲突
$ts = [DateTime]::Now.ToString('yyyyMMddHHmmss')
$custCode = "C-CK-$ts"
$projCode = "P-CK-$ts"
$taskCode = "T-$ts"
$phaseCode = "PH-$ts"
$serialNo = "SN-CK-$ts"

# 临时 JSON 文件目录
$tmpDir = Join-Path $env:TEMP "npdms-smoke-$ts"
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

function Write-Json($name, $obj) {
    $path = Join-Path $tmpDir "$name.json"
    $obj | ConvertTo-Json -Depth 10 -Compress | Set-Content -Path $path -Encoding utf8 -NoNewline
    return $path
}

# Login
$loginBody = Write-Json 'login' @{ username = $username; password = $password; captchaVerification = 'captcha-is-disabled' }
$loginResp = curl.exe -s -X POST "$api/system/auth/login" -H 'Content-Type: application/json' -H 'tenant-id: 1' --data-binary "@$loginBody" | ConvertFrom-Json
$token = $loginResp.data.accessToken
Write-Output "TOKEN: $($token.Substring(0,8))..."
$H = @("Authorization: Bearer $token", 'tenant-id: 1', 'Content-Type: application/json')

function Post($url, $jsonFile) {
    return curl.exe -s -X POST $url -H $H[0] -H $H[1] -H $H[2] --data-binary "@$jsonFile"
}
function Put($url, $jsonFile) {
    return curl.exe -s -X PUT $url -H $H[0] -H $H[1] -H $H[2] --data-binary "@$jsonFile"
}
function Get-Api($url) {
    return curl.exe -s -X GET $url -H $H[0] -H $H[1]
}
function ParseCode($resp) {
    try { return ($resp | ConvertFrom-Json).code } catch { return 500 }
}
function ParseData($resp) {
    try { return ($resp | ConvertFrom-Json).data } catch { return $null }
}

function Invoke-NpdmsSql {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [switch]$Scalar
    )

    $mysqlCommand = if ($Scalar) {
        'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -sN'
    } else {
        'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
    }
    $Sql | & docker compose exec -T mysql sh -c $mysqlCommand
    if ($LASTEXITCODE -ne 0) {
        throw "NPDMS database command failed with exit code $LASTEXITCODE"
    }
}

$results = @{}

# 1. Customer Create
Write-Output "`n=== 1. Customer Create ($custCode) ==="
$f = Write-Json 'cust' @{
    code = $custCode; name = "Customer-$ts"; shortName = "CK-$ts"
    status = 0; address = "Beijing"; remark = "V1-PROJ-A smoke"
}
$r = Post "$api/pms/customer/create" $f
Write-Output $r
$custId = ParseData $r
$results['customer_create'] = if ($custId) { $custId } else { 'FAIL' }

# 2. Customer Contact Create
Write-Output "`n=== 2. Customer Contact Create ==="
$f = Write-Json 'contact' @{
    customerId = $custId; name = "ZhangSan-$ts"; department = "IT"; title = "Manager"
    mobile = "138$ts"; email = "zs-$ts@example.com"
    primaryFlag = $true; status = 0; remark = "primary"
}
$r = Post "$api/pms/customer-contact/create" $f
Write-Output $r
$contactId = ParseData $r
$results['contact_create'] = if ($contactId) { $contactId } else { 'FAIL' }

# 3. Mock Project Insert (SQL) - 实施阶段使用模拟项目
Write-Output "`n=== 3. Mock Project Insert (SQL) ($projCode) ==="
$insertProjectSql = "INSERT INTO pms_project (code, name, customer_id, contract_code, industry, implementation_mode, project_type, shipment_status, source_system, source_business_key, status, version, creator, updater) VALUES ('$projCode', 'Project-$ts', $custId, 'CT-$ts', 'IT', 'ONSITE', 'IMPLEMENT', 'READY', 'MOCK', 'MOCK-$projCode', 0, 0, '1', '1');"
Invoke-NpdmsSql -Sql $insertProjectSql | Out-Null
$results['project_sync'] = 'mocked'

# 4. Project ID Lookup (DB)
Write-Output "`n=== 4. Project ID Lookup (DB) ==="
$projIdRaw = Invoke-NpdmsSql -Sql "SELECT id FROM pms_project WHERE code='$projCode' LIMIT 1" -Scalar
$projId = [int]$projIdRaw.Trim()
Write-Output "project id = $projId"
$results['project_page'] = $projId

# 5. Project Classify (FR-PROJ-010)
Write-Output "`n=== 5. Project Classify ==="
$f = Write-Json 'classify' @{ projectId = $projId; category = "MAJOR"; majorProjectFlag = $true }
$r = Put "$api/pms/project/classify" $f
Write-Output $r
$results['project_classify'] = ParseCode $r

# 6. Project Assign Manager (FR-PROJ-012)
Write-Output "`n=== 6. Project Assign Manager ==="
$f = Write-Json 'assign' @{ projectId = $projId; managerUserId = 1 }
$r = Put "$api/pms/project/assign-manager" $f
Write-Output $r
$results['project_assign'] = ParseCode $r

# 7. Project Team Create (FR-PROJ-013)
Write-Output "`n=== 7. Project Team Create ==="
$f = Write-Json 'team' @{
    projectId = $projId; userId = 1; roleCode = "PROJECT_MANAGER"; roleName = "PM"
    status = 0; remark = "delivery"
}
$r = Post "$api/pms/project-team/create" $f
Write-Output $r
$results['team_create'] = ParseCode $r

# 8. WBS Task Create (FR-PROJ-004)
Write-Output "`n=== 8. Project Task Create ($taskCode) ==="
$f = Write-Json 'task' @{
    projectId = $projId; name = "Requirement-$ts"; code = $taskCode
    description = "Requirement research"; ownerUserId = 1; status = 0
    priority = 1; sort = 0; estimatedHours = 8.0; progress = 0
}
$r = Post "$api/pms/project-task/create" $f
Write-Output $r
$taskId = ParseData $r
$results['task_create'] = if ($taskId) { $taskId } else { 'FAIL' }

# 9. Project Phase Create (FR-PROJ-015/017)
Write-Output "`n=== 9. Project Phase Create ($phaseCode) ==="
$f = Write-Json 'phase' @{
    projectId = $projId; name = "Requirement Phase $ts"; code = $phaseCode
    sort = 0; status = 0; entryCriteria = "project approved"
    exitCriteria = "doc reviewed"; responsibleRole = "PROJECT_MANAGER"; responsibleUserId = 1
}
$r = Post "$api/pms/project-phase/create" $f
Write-Output $r
$phaseId = ParseData $r
$results['phase_create'] = if ($phaseId) { $phaseId } else { 'FAIL' }

# 10. Project Risk Create (FR-PROJ-026)
Write-Output "`n=== 10. Project Risk Create ==="
$f = Write-Json 'risk' @{
    projectId = $projId; title = "Risk-$ts"; riskLevel = "HIGH"; riskType = "requirement"
    cause = "unclear process"; impact = "delivery delay"; mitigation = "more reviews"
    ownerUserId = 1; status = 0; warningThreshold = "3 milestones"
    identifiedAt = "2026-07-29 00:00:00"
}
$r = Post "$api/pms/project-risk/create" $f
Write-Output $r
$riskId = ParseData $r
$results['risk_create'] = if ($riskId) { $riskId } else { 'FAIL' }

# 11. Equipment Create (FR-RES-001)
Write-Output "`n=== 11. Equipment Create ($serialNo) ==="
$f = Write-Json 'equip' @{
    serialNumber = $serialNo; name = "Gateway-$ts"; model = "DP-X3000"
    customerId = $custId; projectId = $projId; location = "Shanghai R01-U12"
    warrantyStartDate = "2026-01-01"; warrantyEndDate = "2028-12-31"; remark = "smoke"
}
$r = Post "$api/pms/equipment/create" $f
Write-Output $r
$equipId = ParseData $r
$results['equipment_create'] = if ($equipId) { $equipId } else { 'FAIL' }

# 12. Equipment Status Change (0->1, FR-RES-002 state machine) via action=DEPLOY
Write-Output "`n=== 12. Equipment Status Change DEPLOY ==="
if ($equipId) {
    $f = Write-Json 'status' @{
        id = $equipId; action = "DEPLOY"; changeDescription = "deploy to customer site"
    }
    $r = Put "$api/pms/equipment/status-change" $f
    Write-Output $r
    $results['equipment_status_change'] = ParseCode $r
} else {
    $results['equipment_status_change'] = 'SKIP'
}

# 13. Project Tree List (FR-PROJ-002)
Write-Output "`n=== 13. Project Tree Descendants ==="
$r = Get-Api "$api/pms/projects/$projId/tree?queryType=DESCENDANTS&pageSize=500"
Write-Output $r
$results['project_tree_list'] = ParseCode $r

# 14. Project Panoramic (FR-PROJ-005/011)
Write-Output "`n=== 14. Project Panoramic ==="
$r = Get-Api "$api/pms/project-panoramic/panoramic?projectId=$projId"
Write-Output $r
$results['project_panoramic'] = ParseCode $r

# 15. Equipment Version History (FR-RES-003)
Write-Output "`n=== 15. Equipment Version History ==="
if ($equipId) {
    $r = Get-Api "$api/pms/equipment/version/list?equipmentId=$equipId"
    Write-Output $r
    $results['equipment_version_list'] = ParseCode $r
} else {
    $results['equipment_version_list'] = 'SKIP'
}

Write-Output "`n=== SUMMARY ==="
$results.GetEnumerator() | ForEach-Object { Write-Output ("{0,-30} = {1}" -f $_.Key, $_.Value) }

# 清理临时文件
Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
