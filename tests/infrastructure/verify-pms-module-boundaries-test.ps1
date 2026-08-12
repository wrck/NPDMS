[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$checker = Join-Path $PSScriptRoot "verify-pms-module-boundaries.ps1"
$modules = @(
    "pms-module-project",
    "pms-module-engineering",
    "pms-module-cutover",
    "pms-module-service",
    "pms-module-asset",
    "pms-module-outsourcing",
    "pms-module-analytics",
    "pms-module-integration"
)

function Write-Utf8File {
    param(
        [string] $Path,
        [string] $Content
    )

    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
}

function New-FixtureRepository {
    $fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("pms-boundary-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null

    $reactorModules = ($modules | ForEach-Object { "        <module>$_</module>" }) -join [Environment]::NewLine
    Write-Utf8File (Join-Path $fixtureRoot "pom.xml") "<project><modules>`n$reactorModules`n</modules></project>"
    $serverDependencies = ($modules | ForEach-Object { "<dependency><artifactId>$_</artifactId></dependency>" }) -join [Environment]::NewLine
    Write-Utf8File (Join-Path $fixtureRoot "yudao-server\pom.xml") "<project><dependencies>$serverDependencies</dependencies></project>"
    $dockerCopies = ($modules | ForEach-Object { "COPY $_ ./$_" }) -join [Environment]::NewLine
    Write-Utf8File (Join-Path $fixtureRoot "docker\backend\Dockerfile") $dockerCopies

    foreach ($module in $modules) {
        Write-Utf8File (Join-Path $fixtureRoot "$module\pom.xml") "<project><artifactId>$module</artifactId></project>"
    }

    $apiModule = "pms-module-project-api"
    Write-Utf8File (Join-Path $fixtureRoot "pms-module-project\$apiModule\pom.xml") "<project><artifactId>$apiModule</artifactId></project>"
    Write-Utf8File (Join-Path $fixtureRoot "pms-module-project\$apiModule\src\main\java\cn\example\pms\project\api\ProjectApi.java") @"
package cn.example.pms.project.api;

public interface ProjectApi {}
"@
    Write-Utf8File (Join-Path $fixtureRoot "pms-module-engineering\pom.xml") @"
<project><artifactId>pms-module-engineering</artifactId><dependencies>
<dependency><artifactId>$apiModule</artifactId></dependency>
</dependencies></project>
"@

    return $fixtureRoot
}

function Assert-CheckerFails {
    param(
        [string] $FixtureRoot,
        [string] $ExpectedMessage
    )

    try {
        & $checker -RepositoryRoot $FixtureRoot
    } catch {
        if ($_.Exception.Message -notlike "*$ExpectedMessage*") {
            throw "Expected '$ExpectedMessage' but received '$($_.Exception.Message)'"
        }
        return
    }
    throw "Expected checker to reject fixture: $ExpectedMessage"
}

$fixtures = @()
try {
    $validFixture = New-FixtureRepository
    $fixtures += $validFixture
    Write-Utf8File (Join-Path $validFixture "pms-module-analytics\src\main\java\AnalyticsSnapshot.java") '@TableName("pms_analytics_snapshot") class AnalyticsSnapshot {}'
    Write-Utf8File (Join-Path $validFixture "pms-module-integration\src\main\java\SyncBatch.java") '@Table(name = "pms_integration_sync_batch") class SyncBatch {}'
    Write-Utf8File (Join-Path $validFixture "pms-module-analytics\src\main\resources\mapper\AnalyticsSnapshotMapper.xml") '<mapper namespace="AnalyticsSnapshotMapper"></mapper>'
    Write-Utf8File (Join-Path $validFixture "pms-module-analytics\src\main\resources\db\migration\V001__create_snapshot.sql") 'CREATE TABLE pms_analytics_snapshot (id bigint);'
    Write-Utf8File (Join-Path $validFixture "pms-module-integration\src\main\resources\db\migration\V010__create_sync_tables.sql") 'CREATE TABLE pms_integration_sync_batch (id bigint);'
    Write-Utf8File (Join-Path $validFixture "pms-module-analytics\src\test\java\ProjectDOTest.java") 'class ProjectDOTest { ProjectDO fixture; }'
    Write-Utf8File (Join-Path $validFixture "pms-module-analytics\src\test\resources\ProjectMapper.xml") '<mapper namespace="ProjectMapper"></mapper>'
    Write-Utf8File (Join-Path $validFixture "pms-module-analytics\src\main\resources\application.xml") '<property name="sample">pms_project</property>'
    & $checker -RepositoryRoot $validFixture

    $apiContractDeclarations = @(
        @{ Extension = ".java"; Declaration = "public final class ProjectCommand {}" },
        @{ Extension = ".java"; Declaration = "public abstract class ProjectQuery {}" },
        @{ Extension = ".java"; Declaration = "public sealed interface ProjectEvent permits ProjectEventImpl {}" },
        @{ Extension = ".java"; Declaration = "public non-sealed interface ProjectEventExtension extends ProjectEvent {}" },
        @{ Extension = ".java"; Declaration = "public record ProjectView() {}" },
        @{ Extension = ".java"; Declaration = "public enum ProjectStatus {}" },
        @{ Extension = ".kt"; Declaration = "public data class ProjectView(val id: Long)" },
        @{ Extension = ".kt"; Declaration = "public open class ProjectExtension" },
        @{ Extension = ".kt"; Declaration = "public value class ProjectCode(val value: String)" }
    )
    foreach ($contract in $apiContractDeclarations) {
        $contractFixture = New-FixtureRepository
        $fixtures += $contractFixture
        Remove-Item -LiteralPath (Join-Path $contractFixture "pms-module-project\pms-module-project-api\src") -Recurse -Force
        Write-Utf8File (Join-Path $contractFixture "pms-module-project\pms-module-project-api\src\main\java\cn\example\pms\project\api\ProjectContract$($contract.Extension)") "package cn.example.pms.project.api;`n$($contract.Declaration)"
        & $checker -RepositoryRoot $contractFixture
    }

    $packageInfoOnlyFixture = New-FixtureRepository
    $fixtures += $packageInfoOnlyFixture
    Remove-Item -LiteralPath (Join-Path $packageInfoOnlyFixture "pms-module-project\pms-module-project-api\src") -Recurse -Force
    Write-Utf8File (Join-Path $packageInfoOnlyFixture "pms-module-project\pms-module-project-api\src\main\java\cn\example\pms\project\api\package-info.java") '/** API package. */ package cn.example.pms.project.api;'
    Assert-CheckerFails -FixtureRoot $packageInfoOnlyFixture -ExpectedMessage "pms-module-project-api must not be empty"

    $testSourceOnlyFixture = New-FixtureRepository
    $fixtures += $testSourceOnlyFixture
    Remove-Item -LiteralPath (Join-Path $testSourceOnlyFixture "pms-module-project\pms-module-project-api\src") -Recurse -Force
    Write-Utf8File (Join-Path $testSourceOnlyFixture "pms-module-project\pms-module-project-api\src\test\java\cn\example\pms\project\api\ProjectApi.java") 'package cn.example.pms.project.api; public interface ProjectApi {}'
    Assert-CheckerFails -FixtureRoot $testSourceOnlyFixture -ExpectedMessage "pms-module-project-api must not be empty"

    $noPackageContractFixture = New-FixtureRepository
    $fixtures += $noPackageContractFixture
    Remove-Item -LiteralPath (Join-Path $noPackageContractFixture "pms-module-project\pms-module-project-api\src") -Recurse -Force
    Write-Utf8File (Join-Path $noPackageContractFixture "pms-module-project\pms-module-project-api\src\main\java\ProjectApi.java") 'public interface ProjectApi {}'
    Assert-CheckerFails -FixtureRoot $noPackageContractFixture -ExpectedMessage "pms-module-project-api must not be empty"

    $emptyApiFixture = New-FixtureRepository
    $fixtures += $emptyApiFixture
    Remove-Item -LiteralPath (Join-Path $emptyApiFixture "pms-module-project\pms-module-project-api\src") -Recurse -Force
    Assert-CheckerFails -FixtureRoot $emptyApiFixture -ExpectedMessage "pms-module-project-api must not be empty"

    $unstableApiFixture = New-FixtureRepository
    $fixtures += $unstableApiFixture
    Write-Utf8File (Join-Path $unstableApiFixture "pms-module-engineering\pom.xml") '<project><artifactId>pms-module-engineering</artifactId></project>'
    Assert-CheckerFails -FixtureRoot $unstableApiFixture -ExpectedMessage "pms-module-project-api must have a stable PMS module caller"

    $testScopeCallerFixture = New-FixtureRepository
    $fixtures += $testScopeCallerFixture
    Write-Utf8File (Join-Path $testScopeCallerFixture "pms-module-engineering\pom.xml") @"
<project><artifactId>pms-module-engineering</artifactId><dependencies>
<dependency><artifactId>pms-module-project-api</artifactId><scope>test</scope></dependency>
</dependencies></project>
"@
    Assert-CheckerFails -FixtureRoot $testScopeCallerFixture -ExpectedMessage "pms-module-project-api must have a stable PMS module caller"

    $unresolvedScopeCallerFixture = New-FixtureRepository
    $fixtures += $unresolvedScopeCallerFixture
    Write-Utf8File (Join-Path $unresolvedScopeCallerFixture "pms-module-engineering\pom.xml") @'
<project><parent><relativePath>../../external-parent/pom.xml</relativePath></parent><artifactId>pms-module-engineering</artifactId><dependencies>
<dependency><artifactId>pms-module-project-api</artifactId><scope>${api.scope}</scope></dependency>
</dependencies></project>
'@
    Assert-CheckerFails -FixtureRoot $unresolvedScopeCallerFixture -ExpectedMessage "unresolved Maven property in direct dependency scope"

    $dependencyManagementCallerFixture = New-FixtureRepository
    $fixtures += $dependencyManagementCallerFixture
    Write-Utf8File (Join-Path $dependencyManagementCallerFixture "pms-module-engineering\pom.xml") @"
<project><artifactId>pms-module-engineering</artifactId><dependencyManagement><dependencies>
<dependency><artifactId>pms-module-project-api</artifactId></dependency>
</dependencies></dependencyManagement></project>
"@
    Assert-CheckerFails -FixtureRoot $dependencyManagementCallerFixture -ExpectedMessage "pms-module-project-api must have a stable PMS module caller"

    $optionalCallerFixture = New-FixtureRepository
    $fixtures += $optionalCallerFixture
    Write-Utf8File (Join-Path $optionalCallerFixture "pms-module-engineering\pom.xml") @"
<project><artifactId>pms-module-engineering</artifactId><dependencies>
<dependency><artifactId>pms-module-project-api</artifactId><optional>true</optional></dependency>
</dependencies></project>
"@
    Assert-CheckerFails -FixtureRoot $optionalCallerFixture -ExpectedMessage "pms-module-project-api must have a stable PMS module caller"

    $unresolvedOptionalCallerFixture = New-FixtureRepository
    $fixtures += $unresolvedOptionalCallerFixture
    Write-Utf8File (Join-Path $unresolvedOptionalCallerFixture "pms-module-engineering\pom.xml") @'
<project><parent><relativePath>../../external-parent/pom.xml</relativePath></parent><artifactId>pms-module-engineering</artifactId><dependencies>
<dependency><artifactId>pms-module-project-api</artifactId><optional>${api.optional}</optional></dependency>
</dependencies></project>
'@
    Assert-CheckerFails -FixtureRoot $unresolvedOptionalCallerFixture -ExpectedMessage "unresolved Maven property in direct dependency optional"

    $flatDependencyFixture = New-FixtureRepository
    $fixtures += $flatDependencyFixture
    Write-Utf8File (Join-Path $flatDependencyFixture "pms-module-asset\pom.xml") @"
<project><artifactId>pms-module-asset</artifactId><dependencies>
<dependency><artifactId>pms-module-project</artifactId></dependency>
</dependencies></project>
"@
    Assert-CheckerFails -FixtureRoot $flatDependencyFixture -ExpectedMessage "must not directly depend on PMS module pms-module-project"

    $propertyDependencyFixture = New-FixtureRepository
    $fixtures += $propertyDependencyFixture
    Write-Utf8File (Join-Path $propertyDependencyFixture "pms-module-asset\pom.xml") @'
<project><artifactId>pms-module-asset</artifactId><properties>
<project-module>pms-module-project</project-module>
</properties><dependencies>
<dependency><artifactId>${project-module}</artifactId></dependency>
</dependencies></project>
'@
    Assert-CheckerFails -FixtureRoot $propertyDependencyFixture -ExpectedMessage "must not directly depend on PMS module pms-module-project"

    $inheritedPropertyDependencyFixture = New-FixtureRepository
    $fixtures += $inheritedPropertyDependencyFixture
    Write-Utf8File (Join-Path $inheritedPropertyDependencyFixture "pom.xml") @'
<project><properties><project-module>pms-module-project</project-module></properties><modules>
<module>pms-module-project</module><module>pms-module-engineering</module><module>pms-module-cutover</module><module>pms-module-service</module>
<module>pms-module-asset</module><module>pms-module-outsourcing</module><module>pms-module-analytics</module><module>pms-module-integration</module>
</modules></project>
'@
    Write-Utf8File (Join-Path $inheritedPropertyDependencyFixture "pms-module-asset\pom.xml") @'
<project><parent><relativePath>../pom.xml</relativePath></parent><artifactId>pms-module-asset</artifactId><dependencies>
<dependency><artifactId>${project-module}</artifactId></dependency>
</dependencies></project>
'@
    Assert-CheckerFails -FixtureRoot $inheritedPropertyDependencyFixture -ExpectedMessage "must not directly depend on PMS module pms-module-project"

    $emptyRelativePathFixture = New-FixtureRepository
    $fixtures += $emptyRelativePathFixture
    Write-Utf8File (Join-Path $emptyRelativePathFixture "pom.xml") @'
<project><properties><project-module>pms-module-project</project-module></properties><modules>
<module>pms-module-project</module><module>pms-module-engineering</module><module>pms-module-cutover</module><module>pms-module-service</module>
<module>pms-module-asset</module><module>pms-module-outsourcing</module><module>pms-module-analytics</module><module>pms-module-integration</module>
</modules></project>
'@
    Write-Utf8File (Join-Path $emptyRelativePathFixture "pms-module-asset\pom.xml") @'
<project><parent><relativePath/></parent><artifactId>pms-module-asset</artifactId><dependencies>
<dependency><artifactId>${project-module}</artifactId></dependency>
</dependencies></project>
'@
    Assert-CheckerFails -FixtureRoot $emptyRelativePathFixture -ExpectedMessage "unresolved Maven property in direct dependency artifactId"

    $missingParentPropertyFixture = New-FixtureRepository
    $fixtures += $missingParentPropertyFixture
    Write-Utf8File (Join-Path $missingParentPropertyFixture "pms-module-asset\pom.xml") @'
<project><parent><relativePath>../../external-parent/pom.xml</relativePath></parent><artifactId>pms-module-asset</artifactId><dependencies>
<dependency><artifactId>${project-module}</artifactId></dependency>
</dependencies></project>
'@
    Assert-CheckerFails -FixtureRoot $missingParentPropertyFixture -ExpectedMessage "unresolved Maven property in direct dependency artifactId"

    $builtinPropertyFixture = New-FixtureRepository
    $fixtures += $builtinPropertyFixture
    Write-Utf8File (Join-Path $builtinPropertyFixture "pom.xml") @'
<project><groupId>cn.example</groupId><artifactId>pms-parent</artifactId><version>1.0.0</version><modules>
<module>pms-module-project</module><module>pms-module-engineering</module><module>pms-module-cutover</module><module>pms-module-service</module>
<module>pms-module-asset</module><module>pms-module-outsourcing</module><module>pms-module-analytics</module><module>pms-module-integration</module>
</modules></project>
'@
    Write-Utf8File (Join-Path $builtinPropertyFixture "pms-module-asset\pom.xml") @'
<project><parent><groupId>cn.example</groupId><artifactId>pms-parent</artifactId><version>1.0.0</version><relativePath>../pom.xml</relativePath></parent><artifactId>pms-module-asset</artifactId><dependencies>
<dependency><artifactId>${project.groupId}</artifactId></dependency><dependency><artifactId>${project.artifactId}</artifactId></dependency><dependency><artifactId>${project.version}</artifactId></dependency>
<dependency><artifactId>${pom.groupId}</artifactId></dependency><dependency><artifactId>${pom.artifactId}</artifactId></dependency><dependency><artifactId>${pom.version}</artifactId></dependency>
<dependency><artifactId>${parent.groupId}</artifactId></dependency><dependency><artifactId>${parent.artifactId}</artifactId></dependency><dependency><artifactId>${parent.version}</artifactId></dependency>
</dependencies></project>
'@
    & $checker -RepositoryRoot $builtinPropertyFixture

    $builtinCrossDomainFixture = New-FixtureRepository
    $fixtures += $builtinCrossDomainFixture
    Write-Utf8File (Join-Path $builtinCrossDomainFixture "pom.xml") @'
<project><groupId>cn.example</groupId><artifactId>pms-module-project</artifactId><version>1.0.0</version><modules>
<module>pms-module-project</module><module>pms-module-engineering</module><module>pms-module-cutover</module><module>pms-module-service</module>
<module>pms-module-asset</module><module>pms-module-outsourcing</module><module>pms-module-analytics</module><module>pms-module-integration</module>
</modules></project>
'@
    Write-Utf8File (Join-Path $builtinCrossDomainFixture "pms-module-asset\pom.xml") @'
<project><parent><groupId>cn.example</groupId><artifactId>pms-module-project</artifactId><version>1.0.0</version><relativePath>../pom.xml</relativePath></parent><artifactId>pms-module-asset</artifactId><dependencies>
<dependency><artifactId>${parent.artifactId}</artifactId></dependency>
</dependencies></project>
'@
    Assert-CheckerFails -FixtureRoot $builtinCrossDomainFixture -ExpectedMessage "must not directly depend on PMS module pms-module-project"

    $javaEntityFixture = New-FixtureRepository
    $fixtures += $javaEntityFixture
    Write-Utf8File (Join-Path $javaEntityFixture "pms-module-analytics\src\main\java\ProjectDO.java") '@TableName("pms_project") class ProjectDO {}'
    Assert-CheckerFails -FixtureRoot $javaEntityFixture -ExpectedMessage "must not own core master data"

    $mapperFixture = New-FixtureRepository
    $fixtures += $mapperFixture
    Write-Utf8File (Join-Path $mapperFixture "pms-module-integration\src\main\resources\mapper\ProjectMapper.xml") '<mapper namespace="ProjectMapper"></mapper>'
    Assert-CheckerFails -FixtureRoot $mapperFixture -ExpectedMessage "must not own core master data"

    $coreMasterData = @(
        @{ Table = "pms_customer"; Type = "CustomerDO" },
        @{ Table = "pms_customer_contact"; Type = "CustomerContactDO" },
        @{ Table = "pms_project_portfolio"; Type = "ProjectPortfolioDO" },
        @{ Table = "pms_project"; Type = "ProjectDO" },
        @{ Table = "pms_project_node"; Type = "ProjectNodeDO" },
        @{ Table = "pms_project_hierarchy_node"; Type = "ProjectHierarchyNodeDO" },
        @{ Table = "pms_project_task_wbs"; Type = "ProjectTaskWbsDO" },
        @{ Table = "pms_project_wbs"; Type = "ProjectWbsDO" },
        @{ Table = "pms_task"; Type = "TaskDO" },
        @{ Table = "pms_project_milestone"; Type = "MilestoneDO" },
        @{ Table = "pms_project_risk"; Type = "RiskDO" },
        @{ Table = "pms_project_issue"; Type = "IssueDO" },
        @{ Table = "pms_project_acceptance"; Type = "AcceptanceDO" },
        @{ Table = "pms_project_closure"; Type = "ClosureDO" },
        @{ Table = "pms_device"; Type = "DeviceDO" },
        @{ Table = "pms_equipment"; Type = "EquipmentDO" },
        @{ Table = "pms_asset"; Type = "AssetDO" },
        @{ Table = "pms_spare_part"; Type = "SparePartDO" },
        @{ Table = "pms_rma"; Type = "RmaDO" }
    )
    foreach ($masterData in $coreMasterData) {
        $migrationFixture = New-FixtureRepository
        $fixtures += $migrationFixture
        Write-Utf8File (Join-Path $migrationFixture "pms-module-integration\src\main\resources\db\migration\V010__create_sync_tables.sql") "CREATE TABLE $($masterData.Table) (id bigint);"
        Assert-CheckerFails -FixtureRoot $migrationFixture -ExpectedMessage "must not own core master data"

        $typeFixture = New-FixtureRepository
        $fixtures += $typeFixture
        Write-Utf8File (Join-Path $typeFixture "pms-module-analytics\src\main\java\$($masterData.Type).java") "class $($masterData.Type) {}"
        Assert-CheckerFails -FixtureRoot $typeFixture -ExpectedMessage "must not own core master data"
    }
} finally {
    foreach ($fixture in $fixtures) {
        if (Test-Path -LiteralPath $fixture) {
            Remove-Item -LiteralPath $fixture -Recurse -Force
        }
    }
}

Write-Output "PMS module boundary checker fixture tests passed."
