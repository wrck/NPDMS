[CmdletBinding()]
param(
    [ValidateSet('all', 'unit', 'integration', 'contract', 'e2e', 'performance', 'security')]
    [string]$Type = 'all',
    [string]$EvidenceDirectory
)

$ErrorActionPreference = 'Stop'
$script:RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Assert-FoundationCondition {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Invoke-HealthCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$ArgumentList
    )

    & $FilePath @ArgumentList
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $global:LASTEXITCODE = 0
        throw "$Name failed with exit code $exitCode"
    }
}

function Get-TestSuiteDefinition {
    @(
        [pscustomobject]@{ TestType = 'unit'; MavenProfile = 'pms-test-unit'; Include = '**/*Test.java'; MavenTest = 'UnitHealthFixtureTest' }
        [pscustomobject]@{ TestType = 'integration'; MavenProfile = 'pms-test-integration'; Include = '**/*IntegrationTest.java'; MavenTest = 'IntegrationHealthFixtureIntegrationTest' }
        [pscustomobject]@{ TestType = 'contract'; MavenProfile = 'pms-test-contract'; Include = '**/*ContractTest.java'; MavenTest = 'ContractHealthFixtureContractTest' }
        [pscustomobject]@{ TestType = 'e2e'; MavenProfile = $null; Include = $null }
        [pscustomobject]@{ TestType = 'performance'; MavenProfile = $null; Include = $null }
        [pscustomobject]@{ TestType = 'security'; MavenProfile = $null; Include = $null }
    )
}

function Get-PomProfileInclude {
    param([string]$ProfileId)

    [xml]$pom = Get-Content -LiteralPath (Join-Path $script:RepositoryRoot 'pom.xml') -Raw -Encoding utf8
    $namespaceManager = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
    $namespaceManager.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
    $profile = $pom.SelectSingleNode("/m:project/m:profiles/m:profile[m:id='$ProfileId']", $namespaceManager)
    Assert-FoundationCondition ($null -ne $profile) "Missing Maven test profile: $ProfileId"

    $include = $profile.SelectSingleNode("m:build/m:plugins/m:plugin[m:artifactId='maven-surefire-plugin']/m:configuration/m:includes/m:include", $namespaceManager)
    Assert-FoundationCondition ($null -ne $include) "Maven test profile $ProfileId has no Surefire include pattern"
    return $include.InnerText
}

function Get-JvmHealthRunner {
    # 优先使用 NPDMS_JAVA_HOME 环境变量；否则探测本地 JDK 25 安装。
    $javaHome = $env:NPDMS_JAVA_HOME
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        $wildcardCandidates = Get-ChildItem 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'jdk-25*' }
        foreach ($candidate in $wildcardCandidates) {
            $javaHome = $candidate.FullName
            break
        }
    }
    Assert-FoundationCondition (-not [string]::IsNullOrWhiteSpace($javaHome)) 'JDK 25 is required to run JVM health checks. Set NPDMS_JAVA_HOME or install JDK 25 under C:\Program Files\Java\.'

    $mvnCommand = Get-Command 'mvn' -ErrorAction SilentlyContinue
    Assert-FoundationCondition ($null -ne $mvnCommand) 'mvn is required to run JVM health checks locally.'

    return [pscustomobject]@{
        FilePath = $mvnCommand.Source
        JavaHome = $javaHome
        FixturePom = 'tests/maven-fixture/pom.xml'
    }
}

function Test-JvmHealth {
    param([ValidateSet('unit', 'integration', 'contract')][string]$TestType)

    $definition = Get-TestSuiteDefinition | Where-Object { $_.TestType -eq $TestType }
    $include = Get-PomProfileInclude -ProfileId $definition.MavenProfile
    Assert-FoundationCondition ($include -eq $definition.Include) "Maven profile $($definition.MavenProfile) must include $($definition.Include)"

    Assert-FoundationCondition (-not [string]::IsNullOrWhiteSpace($definition.MavenTest)) "$TestType health check has no Maven test class"
    $runner = Get-JvmHealthRunner
    # 使用本地 Maven + JDK 25 运行 fixture，不依赖 Docker。
    $previousJavaHome = $env:JAVA_HOME
    $previousPath = $env:PATH
    try {
        $env:JAVA_HOME = $runner.JavaHome
        $env:PATH = "$($runner.JavaHome)\bin;$($env:PATH)"
        Invoke-HealthCommand -Name "$TestType root profile resolution" -FilePath $runner.FilePath -ArgumentList @(
            '-N', "-P$($definition.MavenProfile)", 'validate'
        )
        Invoke-HealthCommand -Name "$TestType Maven health test" -FilePath $runner.FilePath -ArgumentList @(
            '-f', $runner.FixturePom, "-P$($definition.MavenProfile)", 'clean', 'test'
        )
    } finally {
        $env:JAVA_HOME = $previousJavaHome
        $env:PATH = $previousPath
    }

    $reportDirectory = Join-Path $script:RepositoryRoot 'tests/maven-fixture/target/surefire-reports'
    $reports = @(Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    Assert-FoundationCondition ($reports.Count -eq 1) "$TestType Maven profile must execute exactly one classified fixture test"
    Assert-FoundationCondition ($reports[0].Name -eq "TEST-pms.test.foundation.$($definition.MavenTest).xml") "$TestType Maven profile ran the wrong fixture test: $($reports[0].Name)"
    [xml]$report = Get-Content -LiteralPath $reports[0].FullName -Raw -Encoding utf8
    Assert-FoundationCondition ($report.testsuite.tests -eq '1') "$TestType Maven profile did not report one executed test"

    Write-Output "$TestType health check passed"
}

function Test-TraceEvidenceFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    Assert-FoundationCondition (Test-Path -LiteralPath $Path -PathType Leaf) "Evidence file does not exist: $Path"
    $record = Get-Content -LiteralPath $Path -Raw -Encoding utf8 | ConvertFrom-Json
    $requiredFields = @('taskId', 'frIds', 'acceptanceIds', 'testType', 'command', 'result', 'executedAt', 'evidence')
    $properties = @($record.PSObject.Properties.Name)
    foreach ($field in $requiredFields) {
        Assert-FoundationCondition ($properties -contains $field) "Evidence file $Path is missing required field: $field"
    }

    Assert-FoundationCondition ($record.taskId -match '^T-[A-Z0-9-]+$') "Evidence file $Path has an invalid taskId"
    Assert-FoundationCondition (@($record.frIds).Count -gt 0) "Evidence file $Path must identify at least one FR"
    Assert-FoundationCondition (@($record.frIds | Where-Object { $_ -notmatch '^FR-[A-Z]+-\d+$' }).Count -eq 0) "Evidence file $Path has an invalid FR identifier"
    Assert-FoundationCondition (@($record.acceptanceIds).Count -gt 0) "Evidence file $Path must identify at least one acceptance ID"
    Assert-FoundationCondition (@((Get-TestSuiteDefinition).TestType) -contains $record.testType) "Evidence file $Path has an unsupported testType"
    Assert-FoundationCondition (-not [string]::IsNullOrWhiteSpace($record.command)) "Evidence file $Path has an empty command"
    Assert-FoundationCondition (@('passed', 'failed', 'blocked') -contains $record.result) "Evidence file $Path has an invalid result"
    $parsedTimestamp = [DateTime]::MinValue
    Assert-FoundationCondition ([DateTime]::TryParse($record.executedAt, [ref]$parsedTimestamp)) "Evidence file $Path has an invalid executedAt timestamp"
    Assert-FoundationCondition (@($record.evidence).Count -gt 0) "Evidence file $Path must contain at least one evidence path"
    $repositoryRootWithSeparator = $script:RepositoryRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    foreach ($evidencePath in @($record.evidence)) {
        Assert-FoundationCondition (-not [System.IO.Path]::IsPathRooted($evidencePath)) "Evidence path must be repository-relative: $evidencePath"
        $resolvedEvidencePath = [System.IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot $evidencePath))
        Assert-FoundationCondition ($resolvedEvidencePath.StartsWith($repositoryRootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) "Evidence path escapes the repository: $evidencePath"
        Assert-FoundationCondition (Test-Path -LiteralPath $resolvedEvidencePath -PathType Leaf) "Evidence path does not exist: $evidencePath"
    }

    return $record
}

function Get-EvidencePaths {
    param([string]$TestType)

    switch ($TestType) {
        'unit' { return @('pom.xml', 'tests/maven-fixture/pom.xml', 'tests/e2e/verify-test-foundation.ps1') }
        'integration' { return @('pom.xml', 'tests/maven-fixture/pom.xml', 'tests/e2e/verify-test-foundation.ps1') }
        'contract' { return @('pom.xml', 'tests/maven-fixture/pom.xml', 'tests/e2e/verify-test-foundation.ps1') }
        'e2e' { return @('tests/e2e/verify-test-foundation.ps1', 'tests/e2e/verify-test-foundation-test.ps1') }
        'performance' { return @('tests/e2e/verify-test-foundation.ps1', 'tests/performance/verify-performance-health.ps1') }
        'security' { return @('tests/e2e/verify-test-foundation.ps1', 'tests/security/verify-security-health.ps1') }
    }
}

function Write-TraceEvidence {
    param(
        [string]$TestType,
        [string]$TargetDirectory
    )

    if ([string]::IsNullOrWhiteSpace($TargetDirectory)) {
        return
    }

    New-Item -ItemType Directory -Path $TargetDirectory -Force | Out-Null
    $record = [ordered]@{
        taskId = 'T-CP-009'
        frIds = @('FR-PLT-010')
        acceptanceIds = @('T-CP-009-AC-001')
        testType = $TestType
        command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File tests/e2e/verify-test-foundation.ps1 -Type $TestType"
        result = 'passed'
        executedAt = [DateTime]::UtcNow.ToString('o')
        evidence = @(Get-EvidencePaths -TestType $TestType)
    }
    $evidenceFile = Join-Path $TargetDirectory ("T-CP-009-$TestType-health.json")
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $evidenceFile -Encoding utf8
    Test-TraceEvidenceFile -Path $evidenceFile | Out-Null
    Write-Output "Recorded $TestType evidence: $evidenceFile"
}

function Invoke-TestFoundationHealth {
    param(
        [ValidateSet('unit', 'integration', 'contract', 'e2e', 'performance', 'security')]
        [string]$TestType,
        [string]$TargetEvidenceDirectory
    )

    switch ($TestType) {
        { $_ -in @('unit', 'integration', 'contract') } { Test-JvmHealth -TestType $TestType; break }
        'e2e' { & (Join-Path $PSScriptRoot 'verify-test-foundation-test.ps1'); break }
        'performance' { & (Join-Path $script:RepositoryRoot 'tests/performance/verify-performance-health.ps1') -FromUnified; break }
        'security' { & (Join-Path $script:RepositoryRoot 'tests/security/verify-security-health.ps1') -FromUnified; break }
    }
    Write-TraceEvidence -TestType $TestType -TargetDirectory $TargetEvidenceDirectory
}

if ($MyInvocation.InvocationName -ne '.') {
    $testTypes = if ($Type -eq 'all') { (Get-TestSuiteDefinition).TestType } else { @($Type) }
    foreach ($testType in $testTypes) {
        Invoke-TestFoundationHealth -TestType $testType -TargetEvidenceDirectory $EvidenceDirectory
    }
    Write-Output 'test foundation health checks passed'
}
