; Native Inno installer, not an EXE wrapper around MSI. Both consume the same frozen payload.
#ifndef Payload
  #error Payload is required
#endif
#ifndef Version
  #define Version "1.1.3"
#endif
#ifndef DefaultServerUrl
  #define DefaultServerUrl "http://10.210.0.11:59191"
#endif
#ifndef DefaultWebOrigin
  #define DefaultWebOrigin "http://10.210.0.11:19191"
#endif
[Setup]
AppId=NPDMS-DAC-Inno
AppName=NPDMS DAC
AppVersion={#Version}
AppPublisher=NPDMS
DefaultDirName={autopf}\NPDMS\DAC
DisableDirPage=yes
DefaultGroupName=NPDMS
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#OutputDirectory}
OutputBaseFilename=NPDMS-DAC-{#Version}-x64-inno
Compression=lzma2
SolidCompression=yes
UninstallDisplayIcon={app}\NpdmsDac.exe
CloseApplications=no
RestartApplications=no
SetupLogging=yes
WizardStyle=modern

[Languages]
Name: "chinesesimp"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Files]
Source: "{#Payload}\*"; DestDir: "{app}"; Excludes: "NpdmsDac.exe"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#Payload}\NpdmsDac.exe"; DestDir: "{app}"; Flags: ignoreversion; AfterInstall: InitializeService
Source: "{#Payload}\NpdmsDac.exe"; Flags: dontcopy

[Icons]
Name: "{group}\DAC 设置"; Filename: "{app}\NpdmsDac.exe"; Parameters: "--configure"; WorkingDir: "{app}"
Name: "{group}\DAC 本机状态"; Filename: "{app}\NpdmsDac.exe"; Parameters: "--open-status"; WorkingDir: "{app}"

[Registry]
Root: HKLM; Subkey: "Software\Classes\npdms-dac"; ValueType: string; ValueData: "URL:NPDMS DAC"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\Classes\npdms-dac"; ValueName: "URL Protocol"; ValueType: string; ValueData: ""
Root: HKLM; Subkey: "Software\Classes\npdms-dac\shell\open\command"; ValueType: string; ValueData: """{app}\NpdmsDac.exe"" --uri ""%1"""
Root: HKLM; Subkey: "Software\NPDMS\DAC"; ValueName: "ServerUrl"; ValueType: string; ValueData: "{code:GetServerUrl}"
Root: HKLM; Subkey: "Software\NPDMS\DAC"; ValueName: "WebOrigin"; ValueType: string; ValueData: "{code:GetWebOrigin}"

[Run]
Filename: "{app}\NpdmsDac.exe"; Parameters: "--open-status"; Flags: nowait runasoriginaluser skipifsilent; Check: IsServiceReady

; ProgramData is intentionally absent from Files/UninstallDelete. Uninstall preserves configuration, credentials and all history.
[Code]
var Settings: TInputQueryWizardPage;
    ServiceReady: Boolean;
    ServiceCreated: Boolean;
    PreviousMsi: String;

function MsiEnumRelatedProducts(UpgradeCode: String; Reserved, Index: Cardinal; ProductCode: String): Cardinal;
  external 'MsiEnumRelatedProductsW@msi.dll stdcall';
function MsiGetProductInfo(Product, PropertyName, Value: String; var ValueLength: Cardinal): Cardinal;
  external 'MsiGetProductInfoW@msi.dll stdcall';

function InitializeSetup(): Boolean;
var ProductCode, OtherProduct, InstalledVersion: String; Release, MsiResult, VersionLength: Cardinal; OldVersion, NewVersion: Int64;
begin
  ProductCode := StringOfChar(#0, 39);
  Result := False;
  MsiResult := MsiEnumRelatedProducts('{5066BB68-507D-4CBB-85E8-769C0BE81E10}', 0, 0, ProductCode);
  if MsiResult = 0 then begin
    PreviousMsi := Copy(ProductCode, 1, 38);
    OtherProduct := StringOfChar(#0, 39);
    if MsiEnumRelatedProducts('{5066BB68-507D-4CBB-85E8-769C0BE81E10}', 0, 1, OtherProduct) <> 259 then begin
      SuppressibleMsgBox('存在多个或无法确认的旧版 MSI，不能自动卸载，请先检查安装状态。', mbError, MB_OK, IDOK); exit;
    end;
    InstalledVersion := StringOfChar(#0, 256); VersionLength := 255;
    if MsiGetProductInfo(PreviousMsi, 'VersionString', InstalledVersion, VersionLength) <> 0 then begin
      SuppressibleMsgBox('无法读取旧版 MSI 版本，安装未继续。', mbError, MB_OK, IDOK); exit;
    end;
    InstalledVersion := Copy(InstalledVersion, 1, VersionLength);
    if not StrToVersion(InstalledVersion, OldVersion) or not StrToVersion('{#Version}', NewVersion) then exit;
    if ComparePackedVersion(OldVersion, NewVersion) > 0 then begin
      SuppressibleMsgBox('已安装更新版本的 MSI DAC，不能降级卸载。', mbError, MB_OK, IDOK); exit;
    end;
  end else if MsiResult <> 259 then begin
    SuppressibleMsgBox('无法确认已有 MSI 安装状态，安装未继续，请检查 Windows Installer。', mbError, MB_OK, IDOK); exit;
  end;
  if (PreviousMsi <> '') and RegKeyExists(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1') then begin
    SuppressibleMsgBox('同时存在 MSI 与 Inno 安装记录，安装未继续，请先检查旧服务归属。', mbError, MB_OK, IDOK); exit;
  end;
  if not RegQueryDWordValue(HKLM32, 'SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full', 'Release', Release) or (Release < 394254) then begin
    SuppressibleMsgBox('需要 .NET Framework 4.6.1 或更新版本。', mbError, MB_OK, IDOK);
    exit;
  end;
  if RegKeyExists(HKLM64, 'SYSTEM\CurrentControlSet\Services\NpdmsDac') and
    not RegKeyExists(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1') and (PreviousMsi = '') then begin
    SuppressibleMsgBox('检测到由其他方式管理的 NpdmsDac 服务，请先确认其来源，安装器不会覆盖它。', mbError, MB_OK, IDOK);
    exit;
  end;
  if RegQueryStringValue(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1', 'DisplayVersion', InstalledVersion) then
    if not StrToVersion(InstalledVersion, OldVersion) or not StrToVersion('{#Version}', NewVersion) then exit
    else if ComparePackedVersion(OldVersion, NewVersion) > 0 then begin
      SuppressibleMsgBox('已安装更新版本的 NPDMS DAC，不能降级覆盖。', mbError, MB_OK, IDOK);
      exit;
    end;
  Result := True;
end;

procedure InitializeWizard();
var SavedServer, SavedOrigin: String;
begin
  Settings := CreateInputQueryPage(wpWelcome, 'SDP 环境', '配置后端地址与网页来源', '安装前将自动卸载旧版并短暂停止采集；保留配置、连接、凭据及任务历史。环境变更请在 DAC 设置中完成。');
  Settings.Add('NPDMS 后端地址（含协议、端口）', False);
  Settings.Add('NPDMS 网页来源（含协议、端口）', False);
  SavedServer := '{#DefaultServerUrl}'; SavedOrigin := '{#DefaultWebOrigin}';
  RegQueryStringValue(HKLM64, 'Software\NPDMS\DAC', 'ServerUrl', SavedServer);
  RegQueryStringValue(HKLM64, 'Software\NPDMS\DAC', 'WebOrigin', SavedOrigin);
  Settings.Values[0] := ExpandConstant('{param:SERVERURL|' + SavedServer + '}');
  Settings.Values[1] := ExpandConstant('{param:WEBORIGIN|' + SavedOrigin + '}');
end;

function GetServerUrl(Param: String): String;
begin Result := Settings.Values[0]; end;
function GetWebOrigin(Param: String): String;
begin Result := Settings.Values[1]; end;

function RunHelper(FileName, Arguments: String): Boolean;
var Code: Integer;
begin Result := Exec(FileName, Arguments, '', SW_HIDE, ewWaitUntilTerminated, Code) and (Code = 0); end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
var Code, ClosingQuote: Integer; OldCommand, OldDirectory, OldExecutable, Probe, CurrentVersion: String; InnoPresent: Boolean;
    OldVersion, NewVersion: Int64;
    VersionLength: Cardinal;
begin
  Result := '';
  ExtractTemporaryFile('NpdmsDac.exe');
  if not RunHelper(ExpandConstant('{tmp}\NpdmsDac.exe'), '--validate-settings "' + Settings.Values[0] + '" "' + Settings.Values[1] + '"') then begin
    Result := 'NPDMS 地址无效，请填写完整 HTTP/HTTPS 地址；网页来源不能包含路径。'; exit;
  end;
  InnoPresent := RegKeyExists(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1');
  if PreviousMsi <> '' then begin
    if InnoPresent then begin Result := '安装记录已变化，同时存在两种安装器，新版安装未继续。'; exit; end;
    Probe := StringOfChar(#0, 39);
    if MsiEnumRelatedProducts('{5066BB68-507D-4CBB-85E8-769C0BE81E10}', 0, 0, Probe) <> 0 then begin
      Result := '旧 MSI 安装状态已变化，请重新运行安装器。'; exit;
    end;
    if Copy(Probe, 1, 38) <> PreviousMsi then begin Result := '旧 MSI 安装身份已变化，安装未继续。'; exit; end;
    Probe := StringOfChar(#0, 39);
    if MsiEnumRelatedProducts('{5066BB68-507D-4CBB-85E8-769C0BE81E10}', 0, 1, Probe) <> 259 then begin
      Result := '存在多个或无法确认的旧 MSI，安装未继续。'; exit;
    end;
    CurrentVersion := StringOfChar(#0, 256); VersionLength := 255;
    if MsiGetProductInfo(PreviousMsi, 'VersionString', CurrentVersion, VersionLength) <> 0 then begin
      Result := '无法重新确认旧 MSI 版本，安装未继续。'; exit;
    end;
    CurrentVersion := Copy(CurrentVersion, 1, VersionLength);
    if not StrToVersion(CurrentVersion, OldVersion) or not StrToVersion('{#Version}', NewVersion) then begin
      Result := '旧 MSI 版本无效，安装未继续。'; exit;
    end;
    if ComparePackedVersion(OldVersion, NewVersion) > 0 then begin Result := '旧 MSI 已更新，不能降级卸载。'; exit; end;
    if not Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + PreviousMsi + ' /qn /norestart /L*v "' + ExpandConstant('{tmp}\npdms-dac-remove-msi.log') + '"', '', SW_HIDE, ewWaitUntilTerminated, Code) then begin
      Result := '无法启动旧 MSI 卸载，新版安装未继续。'; exit;
    end;
    if Code = 3010 then begin NeedsRestart := True; Result := '旧版已卸载但需要重启，请重启后重新安装；数据目录保留。'; exit; end;
    if Code <> 0 then begin Result := '旧 MSI 卸载失败，退出码 ' + IntToStr(Code) + '，新版安装未继续；数据目录保留。'; exit; end;
    Probe := StringOfChar(#0, 39);
    if MsiEnumRelatedProducts('{5066BB68-507D-4CBB-85E8-769C0BE81E10}', 0, 0, Probe) <> 259 then begin
      Result := '旧 MSI 注册记录尚未清除，新版安装未继续。'; exit;
    end;
    PreviousMsi := '';
  end else if InnoPresent then begin
    Probe := StringOfChar(#0, 39);
    if MsiEnumRelatedProducts('{5066BB68-507D-4CBB-85E8-769C0BE81E10}', 0, 0, Probe) <> 259 then begin
      Result := '安装状态发生变化或同时存在 MSI，新版安装未继续。'; exit;
    end;
    if not RegQueryStringValue(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1', 'DisplayVersion', CurrentVersion)
      or not StrToVersion(CurrentVersion, OldVersion) or not StrToVersion('{#Version}', NewVersion) then begin
      Result := '无法重新确认旧版版本，新版安装未继续。'; exit;
    end;
    if ComparePackedVersion(OldVersion, NewVersion) > 0 then begin Result := '旧版已变化为更新版本，不能降级卸载。'; exit; end;
    if not RegQueryStringValue(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1', 'UninstallString', OldCommand)
      or not RegQueryStringValue(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1', 'InstallLocation', OldDirectory) then begin
      Result := '旧版 Inno 卸载信息缺失，新版安装未继续。'; exit;
    end;
    if (Length(OldCommand) < 3) or (OldCommand[1] <> '"') then begin Result := '旧版卸载入口无效。'; exit; end;
    ClosingQuote := Pos('"', Copy(OldCommand, 2, Length(OldCommand)));
    if ClosingQuote = 0 then begin Result := '旧版卸载入口无效。'; exit; end;
    OldExecutable := Copy(OldCommand, 2, ClosingQuote - 1);
    if (Trim(Copy(OldCommand, ClosingQuote + 2, Length(OldCommand))) <> '')
      or (CompareText(RemoveBackslashUnlessRoot(OldDirectory), RemoveBackslashUnlessRoot(ExpandConstant('{app}'))) <> 0)
      or (CompareText(ExtractFileDir(OldExecutable), RemoveBackslashUnlessRoot(OldDirectory)) <> 0)
      or (Length(ExtractFileName(OldExecutable)) <> 12)
      or (CompareText(Copy(ExtractFileName(OldExecutable), 1, 5), 'unins') <> 0)
      or (Pos(Copy(ExtractFileName(OldExecutable), 6, 1), '0123456789') = 0)
      or (Pos(Copy(ExtractFileName(OldExecutable), 7, 1), '0123456789') = 0)
      or (Pos(Copy(ExtractFileName(OldExecutable), 8, 1), '0123456789') = 0)
      or (CompareText(ExtractFileExt(OldExecutable), '.exe') <> 0)
      or not FileExists(OldExecutable) then begin
      Result := '旧版卸载入口不属于本机 DAC 安装目录，安装未继续。'; exit;
    end;
    if not Exec(OldExecutable, '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /LOG="' + ExpandConstant('{tmp}\npdms-dac-remove-inno.log') + '"', '', SW_HIDE, ewWaitUntilTerminated, Code) then begin
      Result := '无法启动旧版 Inno 卸载，新版安装未继续。'; exit;
    end;
    if Code <> 0 then begin Result := '旧版 Inno 卸载失败，退出码 ' + IntToStr(Code) + '，新版安装未继续；数据目录保留。'; exit; end;
    if RegKeyExists(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1') then begin
      Result := '旧版 Inno 注册记录尚未清除，新版安装未继续。'; exit;
    end;
  end;
  if RegKeyExists(HKLM64, 'SYSTEM\CurrentControlSet\Services\NpdmsDac') then begin
    Result := '原 DAC 服务尚未删除或服务来源不明，新版安装未继续；请检查旧版卸载日志。'; exit;
  end;
end;

procedure InitializeService();
begin
  if not RunHelper(ExpandConstant('{app}\NpdmsDac.exe'), '--initialize "' + Settings.Values[0] + '" "' + Settings.Values[1] + '"') then
    RaiseException('配置初始化失败，请检查安装日志。');
  if not RunHelper(ExpandConstant('{app}\NpdmsDac.exe'), '--service-install') then RaiseException('DAC 服务注册失败。');
  ServiceCreated := True;
  if not RunHelper(ExpandConstant('{app}\NpdmsDac.exe'), '--service-start') then RaiseException('DAC 服务未能启动，请检查本机日志。');
  ServiceReady := True;
end;

function IsServiceReady(): Boolean;
begin Result := ServiceReady; end;

procedure DeinitializeSetup();
begin
  if ServiceCreated and not ServiceReady then begin
    RunHelper(ExpandConstant('{app}\NpdmsDac.exe'), '--service-stop');
    RunHelper(ExpandConstant('{app}\NpdmsDac.exe'), '--service-uninstall');
    RunHelper(ExpandConstant('{app}\NpdmsDac.exe'), '--remove-browser-policy');
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var Removed: Boolean;
begin
  if CurUninstallStep <> usUninstall then exit;
  Removed := RunHelper(ExpandConstant('{app}\NpdmsDac.exe'), '--service-stop');
  if Removed then Removed := RunHelper(ExpandConstant('{app}\NpdmsDac.exe'), '--service-uninstall');
  if Removed then Removed := RunHelper(ExpandConstant('{app}\NpdmsDac.exe'), '--remove-browser-policy');
  if not Removed then RaiseException('DAC 服务或浏览器策略清理失败，卸载未继续。请检查服务与管理员权限。');
end;
