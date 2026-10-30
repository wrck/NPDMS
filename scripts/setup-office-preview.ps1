param()
$ErrorActionPreference = 'Stop'
# Isolated administrative image, not a global Office installation or a Windows service.
$taskOfficeVersion = '26.2.6'
$taskOfficeSha256 = 'f9877032fd908beb9c0ddf06df4af5c2e85f419c42e14876c4cce5aae5fb2660'
$taskOfficeCache = Join-Path ([Environment]::GetFolderPath('UserProfile')) ".cache/npdms/office-preview/$taskOfficeVersion"
$taskOfficeRuntime = Join-Path $taskOfficeCache 'runtime'
$taskOfficeExecutable = Join-Path $taskOfficeRuntime 'program/soffice.com'
if (Test-Path -LiteralPath $taskOfficeExecutable) {
    & $taskOfficeExecutable --version
    if ($LASTEXITCODE -eq 0) { return }
    throw 'Existing Office preview runtime is not executable; no files were replaced.'
}
New-Item -ItemType Directory -Path $taskOfficeCache -Force | Out-Null
$taskOfficeName = "LibreOffice_${taskOfficeVersion}_Win_x86-64.msi"
$taskOfficeInstaller = Join-Path $taskOfficeCache $taskOfficeName
if (!(Test-Path -LiteralPath $taskOfficeInstaller)) {
    Invoke-WebRequest -Uri "https://download.documentfoundation.org/libreoffice/stable/$taskOfficeVersion/win/x86_64/$taskOfficeName" -OutFile $taskOfficeInstaller
}
if ((Get-FileHash -LiteralPath $taskOfficeInstaller -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskOfficeSha256) {
    throw 'Office preview installer does not match the official pinned checksum.'
}
if ((Get-AuthenticodeSignature -LiteralPath $taskOfficeInstaller).Status -ne 'Valid') {
    throw 'Office preview installer signature is invalid.'
}
New-Item -ItemType Directory -Path $taskOfficeRuntime -Force | Out-Null
$taskOfficeProcess = Start-Process -FilePath msiexec.exe -ArgumentList @('/a',('"'+$taskOfficeInstaller+'"'),'/qn','REBOOT=ReallySuppress',('TARGETDIR="'+$taskOfficeRuntime+'"')) -WindowStyle Hidden -Wait -PassThru
if ($taskOfficeProcess.ExitCode -ne 0) { throw "Office preview extraction failed: $($taskOfficeProcess.ExitCode)" }
& $taskOfficeExecutable --version
if ($LASTEXITCODE -ne 0) { throw 'Office preview runtime validation failed.' }
