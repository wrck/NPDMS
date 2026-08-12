[CmdletBinding()]
param(
    [string] $RepositoryRoot = (Join-Path $PSScriptRoot "..\..")
)

$ErrorActionPreference = "Stop"
$repoRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
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
$rootPom = Join-Path $repoRoot "pom.xml"
$serverPom = Join-Path $repoRoot "yudao-server\pom.xml"
$dockerfile = Join-Path $repoRoot "docker\backend\Dockerfile"
$mavenPropertyCache = @{}

function Get-MavenDependencies {
    param([string] $PomPath)

    [xml] $pom = Get-Content -Raw -Encoding UTF8 $PomPath
    $properties = Get-MavenProjectProperties -PomPath $PomPath
    $dependencies = $pom.SelectNodes("/*[local-name()='project']/*[local-name()='dependencies']/*[local-name()='dependency']")
    foreach ($dependency in $dependencies) {
        $artifactId = Get-MavenChildText -Node $dependency -ElementName "artifactId"
        if ([string]::IsNullOrWhiteSpace($artifactId)) {
            continue
        }
        $scope = Get-MavenChildText -Node $dependency -ElementName "scope"
        $optional = Get-MavenChildText -Node $dependency -ElementName "optional"
        $resolvedArtifactId = Resolve-MavenPropertyValue -Value $artifactId -Properties $properties
        if ($resolvedArtifactId -match '\$\{[^}]+\}') {
            throw "$PomPath has an unresolved Maven property in direct dependency artifactId: $resolvedArtifactId"
        }
        $resolvedScope = if ([string]::IsNullOrWhiteSpace($scope)) { "compile" } else { Resolve-MavenPropertyValue -Value $scope -Properties $properties }
        if ($resolvedScope -match '\$\{[^}]+\}') {
            throw "$PomPath has an unresolved Maven property in direct dependency scope: $resolvedScope"
        }
        $resolvedOptional = Resolve-MavenPropertyValue -Value $optional -Properties $properties
        if ($resolvedOptional -match '\$\{[^}]+\}') {
            throw "$PomPath has an unresolved Maven property in direct dependency optional: $resolvedOptional"
        }
        [pscustomobject]@{
            ArtifactId = $resolvedArtifactId
            Scope = $resolvedScope
            Optional = [string]::Equals($resolvedOptional, "true", [System.StringComparison]::OrdinalIgnoreCase)
        }
    }
}

function Get-MavenChildText {
    param(
        [System.Xml.XmlNode] $Node,
        [string] $ElementName
    )

    $element = $Node.SelectSingleNode("./*[local-name()='$ElementName']")
    if ($null -eq $element) {
        return $null
    }
    $element.InnerText.Trim()
}

function Get-MavenProjectProperties {
    param([string] $PomPath)

    $resolvedPomPath = [System.IO.Path]::GetFullPath($PomPath)
    if ($mavenPropertyCache.ContainsKey($resolvedPomPath)) {
        return $mavenPropertyCache[$resolvedPomPath]
    }

    $properties = @{}
    $mavenPropertyCache[$resolvedPomPath] = $properties
    [xml] $pom = Get-Content -Raw -Encoding UTF8 $resolvedPomPath
    $parent = $pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='parent']")
    $parentProperties = @{}
    if ($null -ne $parent) {
        $relativePathElement = $parent.SelectSingleNode("./*[local-name()='relativePath']")
        if ($null -eq $relativePathElement) {
            $relativePath = "../pom.xml"
        } elseif ([string]::IsNullOrWhiteSpace($relativePathElement.InnerText)) {
            $relativePath = $null
        } else {
            $relativePath = $relativePathElement.InnerText.Trim()
        }
        if ($null -ne $relativePath) {
            $parentPomPath = [System.IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $resolvedPomPath) $relativePath))
            if (Test-Path -LiteralPath $parentPomPath -PathType Leaf) {
                $parentProperties = Get-MavenProjectProperties -PomPath $parentPomPath
                foreach ($property in $parentProperties.GetEnumerator()) {
                    $properties[$property.Key] = $property.Value
                }
            }
        }
    }

    $localProperties = $pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='properties']")
    if ($null -ne $localProperties) {
        foreach ($property in $localProperties.ChildNodes | Where-Object { $_.NodeType -eq [System.Xml.XmlNodeType]::Element }) {
            $properties[$property.LocalName] = $property.InnerText.Trim()
        }
    }

    $parentCoordinates = @{}
    if ($null -ne $parent) {
        foreach ($coordinate in @("groupId", "artifactId", "version")) {
            $parentValue = Get-MavenChildText -Node $parent -ElementName $coordinate
            if ([string]::IsNullOrWhiteSpace($parentValue) -and $parentProperties.ContainsKey("project.$coordinate")) {
                $parentValue = $parentProperties["project.$coordinate"]
            }
            if (-not [string]::IsNullOrWhiteSpace($parentValue)) {
                $parentCoordinates[$coordinate] = Resolve-MavenPropertyValue -Value $parentValue -Properties $properties
                $properties["parent.$coordinate"] = $parentCoordinates[$coordinate]
            }
        }
    }

    $modelCoordinates = @{
        "groupId" = Get-MavenChildText -Node $pom.DocumentElement -ElementName "groupId"
        "artifactId" = Get-MavenChildText -Node $pom.DocumentElement -ElementName "artifactId"
        "version" = Get-MavenChildText -Node $pom.DocumentElement -ElementName "version"
    }
    foreach ($coordinate in @("groupId", "version")) {
        if ([string]::IsNullOrWhiteSpace($modelCoordinates[$coordinate]) -and $parentCoordinates.ContainsKey($coordinate)) {
            $modelCoordinates[$coordinate] = $parentCoordinates[$coordinate]
        }
    }
    foreach ($coordinate in @("groupId", "version", "artifactId")) {
        if (-not [string]::IsNullOrWhiteSpace($modelCoordinates[$coordinate])) {
            $modelValue = Resolve-MavenPropertyValue -Value $modelCoordinates[$coordinate] -Properties $properties
            $properties["project.$coordinate"] = $modelValue
            $properties["pom.$coordinate"] = $modelValue
        }
    }
    return $properties
}

function Resolve-MavenPropertyValue {
    param(
        [string] $Value,
        [hashtable] $Properties
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $Value
    }

    $resolvedValue = $Value.Trim()
    for ($i = 0; $i -lt 10; $i++) {
        $changed = $false
        foreach ($match in [regex]::Matches($resolvedValue, '\$\{([^}]+)\}')) {
            $propertyName = $match.Groups[1].Value
            if ($Properties.ContainsKey($propertyName)) {
                $resolvedValue = $resolvedValue.Replace($match.Value, [string] $Properties[$propertyName])
                $changed = $true
            }
        }
        if (-not $changed) {
            break
        }
    }
    return $resolvedValue
}

function Get-ModulePomPath {
    param([string] $Module)

    Join-Path $repoRoot "$Module\pom.xml"
}

function Assert-ApiModuleHasStableCaller {
    param(
        [string] $Module,
        [string] $ModulePath
    )

    $apiArtifactId = "$Module-api"
    $apiModulePath = Join-Path $ModulePath $apiArtifactId
    if (-not (Test-Path -LiteralPath $apiModulePath)) {
        return
    }

    $apiPom = Join-Path $apiModulePath "pom.xml"
    if (-not (Test-Path -LiteralPath $apiPom)) {
        throw "$apiArtifactId must declare its Maven POM."
    }
    $apiSourceRoot = Join-Path $apiModulePath "src\main"
    $apiSources = if (Test-Path -LiteralPath $apiSourceRoot) {
        Get-ChildItem -Path $apiSourceRoot -Recurse -File |
            Where-Object { $_.Extension -in @(".java", ".kt") -and $_.Name -ne "package-info.java" }
    } else {
        @()
    }
    $hasPublicContract = $false
    foreach ($apiSource in $apiSources) {
        $content = Get-Content -Raw -Encoding UTF8 $apiSource.FullName
        $hasPackage = $content -match "(?m)^\s*package\s+[A-Za-z_][A-Za-z0-9_.]*\s*;?"
        $hasPublicDeclaration = $content -match "(?m)^\s*public\s+(?:(?:final|abstract|sealed|non-sealed)\s+)*(?:interface|class|record|enum)\b" -or
            $content -match "(?m)^\s*public\s+(?:(?:open|value|abstract|final|sealed|data|enum)\s+)*(?:interface|class)\b"
        if ($hasPackage -and $hasPublicDeclaration) {
            $hasPublicContract = $true
            break
        }
    }
    if (-not $hasPublicContract) {
        throw "$apiArtifactId must not be empty."
    }

    $hasStableCaller = $false
    foreach ($candidate in $modules | Where-Object { $_ -ne $Module }) {
        $candidateDependencies = Get-MavenDependencies (Get-ModulePomPath $candidate)
        if ($candidateDependencies | Where-Object {
                $_.ArtifactId -eq $apiArtifactId -and $_.Scope -ne "test" -and -not $_.Optional
            }) {
            $hasStableCaller = $true
            break
        }
    }
    if (-not $hasStableCaller) {
        throw "$apiArtifactId must have a stable PMS module caller."
    }
}

function Assert-NoDirectPmsModuleDependency {
    param([string] $Module)

    foreach ($dependency in Get-MavenDependencies (Get-ModulePomPath $Module)) {
        if ($dependency.ArtifactId -ne $Module -and $dependency.ArtifactId -in $modules) {
            throw "$Module must not directly depend on PMS module $($dependency.ArtifactId); use its stable API contract or domain event."
        }
        if ($dependency.ArtifactId -match "^pms-module-.+-biz$") {
            throw "$Module must not directly depend on PMS -biz module $($dependency.ArtifactId)."
        }
    }
}

function Test-CoreMasterDataReference {
    param([string] $Content)

    $coreMasterDataOwnership = @(
        @{ Name = "customer"; Tables = @("pms_customer"); Types = @("Customer") },
        @{ Name = "customer contact"; Tables = @("pms_customer_contact"); Types = @("CustomerContact") },
        @{ Name = "project portfolio"; Tables = @("pms_project_portfolio"); Types = @("ProjectPortfolio") },
        @{ Name = "project"; Tables = @("pms_project"); Types = @("Project") },
        @{ Name = "project node"; Tables = @("pms_project_node", "pms_project_hierarchy_node"); Types = @("ProjectNode", "ProjectHierarchyNode") },
        @{ Name = "project WBS"; Tables = @("pms_project_task_wbs", "pms_project_wbs"); Types = @("ProjectTaskWbs", "ProjectWbs") },
        @{ Name = "task"; Tables = @("pms_task"); Types = @("Task") },
        @{ Name = "milestone"; Tables = @("pms_project_milestone"); Types = @("Milestone") },
        @{ Name = "risk"; Tables = @("pms_project_risk"); Types = @("Risk") },
        @{ Name = "issue"; Tables = @("pms_project_issue"); Types = @("Issue") },
        @{ Name = "acceptance"; Tables = @("pms_project_acceptance"); Types = @("Acceptance") },
        @{ Name = "closure"; Tables = @("pms_project_closure"); Types = @("Closure") },
        @{ Name = "device"; Tables = @("pms_device", "pms_equipment"); Types = @("Device", "Equipment") },
        @{ Name = "asset"; Tables = @("pms_asset", "pms_spare_part", "pms_rma"); Types = @("Asset", "SparePart", "Rma") }
    )
    foreach ($owner in $coreMasterDataOwnership) {
        foreach ($table in $owner.Tables) {
            if ($Content -match "(?i)(?<![A-Za-z0-9_])$([regex]::Escape($table))(?![A-Za-z0-9_])") {
                return $true
            }
        }
        foreach ($type in $owner.Types) {
            if ($Content -match "(?i)\b$([regex]::Escape($type))(?:DO|Entity|Mapper|Repository)\b") {
                return $true
            }
        }
    }
    return $false
}

function Assert-ReadOnlyOrIntegrationDataOwnership {
    param([string] $Module)

    $modulePath = Join-Path $repoRoot $Module
    $persistenceFiles = @()
    foreach ($sourcePath in @(
        (Join-Path $modulePath "src\main\java"),
        (Join-Path $modulePath "src\main\kotlin")
    )) {
        if (Test-Path -LiteralPath $sourcePath) {
            $persistenceFiles += Get-ChildItem -Path $sourcePath -Recurse -File |
                Where-Object { $_.Extension -in @(".java", ".kt") }
        }
    }
    $mapperPath = Join-Path $modulePath "src\main\resources\mapper"
    if (Test-Path -LiteralPath $mapperPath) {
        $persistenceFiles += Get-ChildItem -Path $mapperPath -Recurse -File -Filter "*.xml"
    }
    $migrationPath = Join-Path $modulePath "src\main\resources\db\migration"
    if (Test-Path -LiteralPath $migrationPath) {
        $persistenceFiles += Get-ChildItem -Path $migrationPath -File -Filter "*.sql"
    }

    foreach ($file in $persistenceFiles) {
        $content = Get-Content -Raw -Encoding UTF8 $file.FullName
        if (Test-CoreMasterDataReference $content) {
            throw "$Module must not own core master data: $($file.FullName)"
        }
    }
}

foreach ($required in @($rootPom, $serverPom, $dockerfile)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required assembly file is missing: $required"
    }
}

$rootPomContent = Get-Content -Raw -Encoding UTF8 $rootPom
$serverPomContent = Get-Content -Raw -Encoding UTF8 $serverPom
$dockerfileContent = Get-Content -Raw -Encoding UTF8 $dockerfile

foreach ($module in $modules) {
    $modulePath = Join-Path $repoRoot $module
    $modulePom = Join-Path $modulePath "pom.xml"
    if (-not (Test-Path -LiteralPath $modulePom)) {
        throw "PMS module is missing its Maven POM: $modulePom"
    }
    if ($rootPomContent -notmatch [regex]::Escape("<module>$module</module>")) {
        throw "Root Maven reactor does not include $module."
    }
    if ($serverPomContent -notmatch [regex]::Escape("<artifactId>$module</artifactId>")) {
        throw "yudao-server does not assemble $module."
    }
    if ($dockerfileContent -notmatch [regex]::Escape("COPY $module ./$module")) {
        throw "Docker build context does not include $module."
    }

    Assert-ApiModuleHasStableCaller -Module $module -ModulePath $modulePath
    Assert-NoDirectPmsModuleDependency -Module $module
}

foreach ($readModelModule in @("pms-module-analytics", "pms-module-integration")) {
    Assert-ReadOnlyOrIntegrationDataOwnership -Module $readModelModule
}

Write-Output "PMS module boundary verification passed."
