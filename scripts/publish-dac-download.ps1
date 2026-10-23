param(
    [Parameter(Mandatory = $true)][string]$PackageDirectory
)
$ErrorActionPreference = 'Stop'
$dacPackageRoot = (Resolve-Path -LiteralPath $PackageDirectory).Path
$dacPackageResult = Get-Content -LiteralPath (Join-Path $dacPackageRoot 'package-result.json') -Raw | ConvertFrom-Json
if ($dacPackageResult.version -ne '1.0.6' -or $dacPackageResult.installerConfigurationValidation -ne 'passed-real-msi-session') {
    throw 'The page requires the validated DAC 1.0.6 installer.'
}
$dacInstaller = Join-Path $dacPackageRoot 'NPDMS-DAC-1.0.6-x64.msi'
if ((Get-Item -LiteralPath $dacInstaller).Length -ne $dacPackageResult.installerBytes) { throw 'Installer size differs from package result.' }
$dacPublicDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'yudao-ui/yudao-ui-admin-vue3/public/downloads/dac'
New-Item -ItemType Directory -Path $dacPublicDirectory -Force | Out-Null
Copy-Item -LiteralPath $dacInstaller -Destination (Join-Path $dacPublicDirectory 'NPDMS-DAC-1.0.6-x64.msi')
Write-Output 'Published /downloads/dac/NPDMS-DAC-1.0.6-x64.msi; build the frontend after publishing for a static deployment.'
