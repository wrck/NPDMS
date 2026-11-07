"""Derive a fresh-machine-only Inno candidate from the unchanged native installer.

This does not run an installation or manufacture a frozen-release/MSI validation.
Compile Candidate.iss with Payload, DesktopPayload, CandidateNotice and
OutputDirectory defines. Native inventory, desktop source, frontend and service
adapter are checked before preparing output; staged files get a hash inventory.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess

ORIGINAL_SHA256 = '59e4f9db7d8e51ae2d03e6d118d6624b8fa306c723f424daa97b42ce9206ac3c'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('Installer source contract changed; review required')
    return text.replace(old, new, 1)


FRESH_GUARD = r'''
{ Candidate scope: never migrate, replace or delete an existing installation/data. }
function CandidateFreshState(): Boolean;
var Probe: String;
begin
  Result := False;
  if RegKeyExists(HKLM64, 'SYSTEM\CurrentControlSet\Services\NpdmsDac')
    or RegKeyExists(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-DAC-Inno_is1')
    or RegKeyExists(HKLM64, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\NPDMS-Standalone-Candidate_is1')
    or RegKeyExists(HKLM64, 'Software\NPDMS\DAC')
    or RegKeyExists(HKLM64, 'Software\Classes\sdp-dac')
    or DirExists(ExpandConstant('{autopf}\SDP\DAC'))
    or DirExists(ExpandConstant('{autopf}\NPDMS\DAC'))
    or DirExists(ExpandConstant('{commonappdata}\NPDMS\DAC')) then exit;
  Probe := StringOfChar(#0, 39);
  if MsiEnumRelatedProducts('{5066BB68-507D-4CBB-85E8-769C0BE81E10}', 0, 0, Probe) <> 259 then exit;
  Result := True;
end;

'''


def derive(source):
    if hashlib.sha256(source.encode()).hexdigest() != ORIGINAL_SHA256:
        raise ValueError('Native installer source differs from reviewed baseline')
    text = once(source, '; Native Inno installer, not an EXE wrapper around MSI. Both consume the same frozen payload.',
                '; Installation-validation candidate derived from native Inno source; payload is NOT frozen.')
    text = '#define DefaultServerUrl ""\n#define DefaultWebOrigin ""\n' + text
    text = once(text, 'AppId=NPDMS-DAC-Inno', 'AppId=NPDMS-Standalone-Candidate')
    text = once(text, 'AppName=SDP DAC', 'AppName=SDP 独立安装验证候选')
    text = once(text, 'OutputBaseFilename=SDP-DAC-{#Version}-x64-inno',
                'OutputBaseFilename=SDP-Standalone-{#Version}-Windows-x64-CANDIDATE')
    text = once(text, 'WizardStyle=modern', 'WizardStyle=modern\nInfoBeforeFile={#CandidateNotice}')
    text = once(text, '[Icons]', 'Source: "{#DesktopPayload}\\*"; DestDir: "{app}\\desktop"; Flags: ignoreversion recursesubdirs createallsubdirs\n\n[Icons]')
    text = once(text, '[Registry]', 'Name: "{group}\\桌面候选"; Filename: "{app}\\desktop\\NPDMS Windows Service Candidate.exe"; WorkingDir: "{app}\\desktop"\n\n[Registry]')
    text = once(text, 'function InitializeSetup(): Boolean;', FRESH_GUARD + 'function InitializeSetup(): Boolean;')
    text = once(text, "  ProductCode := StringOfChar(#0, 39);\n  Result := False;", """  ProductCode := StringOfChar(#0, 39);
  Result := False;
  if not CandidateFreshState() then begin
    SuppressibleMsgBox('本候选仅支持全新测试环境。检测到旧安装、服务、配置或遗留数据，安装已停止；不会升级、卸载旧版或删除数据。', mbError, MB_OK, IDOK); exit;
  end;""")
    text = once(text, '安装前将自动卸载旧版并短暂停止采集；保留配置、连接、凭据及任务历史。环境变更请在 DAC 设置中完成。',
                '仅用于全新测试环境；遇到旧安装或数据即停止。桌面组件需要同一个 HTTPS 后端和网页来源，网页来源留空时使用后端地址。')
    text = once(text, "  Result := '';\n  ExtractTemporaryFile('SdpDac.exe');", """  Result := '';
  if not CandidateFreshState() then begin
    Result := '安装状态已变化或发现已有数据，候选未继续。请使用全新测试环境。'; exit;
  end;
  if Settings.Values[1] = '' then Settings.Values[1] := Settings.Values[0];
  if (CompareText(Settings.Values[0], Settings.Values[1]) <> 0)
    or (Pos('https://', Lowercase(Settings.Values[0])) <> 1)
    or (Pos('"', Settings.Values[0]) <> 0) or (Pos(#13, Settings.Values[0]) <> 0) or (Pos(#10, Settings.Values[0]) <> 0) then begin
    Result := '桌面候选需要同源 HTTPS 地址，不得包含引号或换行。'; exit;
  end;
  ExtractTemporaryFile('SdpDac.exe');""")
    return text


def verify_native(root):
    record = json.loads((root / 'candidate-inputs.json').read_text())
    if record.get('candidateOnly') is not True or record.get('releaseFrozen') is not False:
        raise ValueError('Explicit unfrozen native candidate required')
    expected = set()
    for item in record['files']:
        rel = PurePosixPath(item['path'])
        if rel.is_absolute() or '..' in rel.parts or '\\' in item['path'] or ':' in item['path']:
            raise ValueError('Unsafe native inventory path')
        if item['path'] in expected:
            raise ValueError('Duplicate native inventory path')
        expected.add(item['path'])
        file = root / item['path']
        if file.is_symlink() or file.stat().st_size != item['bytes'] or digest(file) != item['sha256']:
            raise ValueError('Native payload changed: ' + item['path'])
    actual = {p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()}
    if actual != expected | {'candidate-inputs.json'} or any(p.is_symlink() for p in root.rglob('*')):
        raise ValueError('Unrecorded native payload file/link')
    required = {'SdpDac.exe', 'SdpDacHost.exe', 'SdpDacService.exe', 'SdpDacService.xml',
                'runtime/bin/java.exe', 'app/device-ops-server.jar', 'parser-release/manifest.json'}
    if not required <= expected:
        raise ValueError('Missing native installation component')
    return record


def verify_desktop(repo, desktop):
    if any(p.is_symlink() for p in desktop.rglob('*')):
        raise ValueError('Desktop links are not supported')
    app = desktop / 'resources/app'
    for directory in ['src', 'resources']:
        for file in (repo / 'npdms-desktop' / directory).rglob('*'):
            if file.is_file() and digest(file) != digest(app / directory / file.relative_to(repo / 'npdms-desktop' / directory)):
                raise ValueError('Desktop source differs from reviewed worktree')
    subprocess.run(['node', str(repo / 'npdms-desktop/scripts/verify-frontend.cjs'),
                    str(app / 'resources/frontend')], check=True, capture_output=True)
    helper = app / 'resources/windows-service/NpdmsServiceBridge.exe'
    bridge = json.loads((helper.parent / 'bridge-manifest.json').read_text())
    if bridge.get('ownershipBoundary') != 'scm-process-listener' or digest(helper) != bridge['sha256']:
        raise ValueError('Windows service adapter mismatch')
    if not (desktop / 'NPDMS Windows Service Candidate.exe').is_file():
        raise ValueError('Desktop executable missing')


def prepare(native, desktop, output):
    repo = Path(__file__).resolve().parents[2]
    source = (repo / 'device-ops-platform/packaging/windows/Package.iss').read_text()
    candidate = derive(source)
    native_record = verify_native(native)
    verify_desktop(repo, desktop)
    output.mkdir(parents=True, exist_ok=False)
    shutil.copytree(desktop, output / 'desktop')
    # Clarify component versus enclosing installer; no runtime/security source changes.
    (output / 'desktop/resources/app/CANDIDATE-NOTICE.txt').write_text(
        '本目录为桌面组件。独立候选安装程序同时安装原生 DAC、Java 运行时及服务。\n'
        '未签名，未经真实 Windows、DPAPI、业务及升级验收；真实离线业务未启用。\n', encoding='utf-8-sig')
    renderer = output / 'desktop/resources/app/resources/renderer.js'
    renderer.write_text(once(once(renderer.read_text(), '须先安装原生 Windows DAC，', '独立安装程序已配置原生 Windows DAC，'), '本安装候选不安装或升级原生服务。', '原生服务由独立安装程序配置，桌面退出不会停止该服务。'))
    notice = ('SDP Windows 独立安装验证候选\n\n'
              '本包包含桌面客户端、原生 DAC、JRE、WinSW 和解析器；无需预装 DAC，需要 Windows x64、.NET Framework 4.6.1 或更高版本及管理员安装权限。\n'
              '仅用于全新测试环境。发现旧 MSI/Inno、服务、配置或遗留数据即停止，不执行升级迁移。卸载保留 ProgramData 中的配置、密钥、连接与任务历史。\n'
              '请输入同源 HTTPS 后端地址。安装会注册并启动原生服务；安装后从开始菜单手动启动桌面候选，退出桌面不会停止共享服务。\n'
              '未签名；正式冻结脚本和权威命令预设仍缺失；尚未在真实 Windows 验证安装、卸载、权限、DPAPI 和业务闭环。真实离线业务未启用。\n')
    (output / 'candidate-notice.txt').write_text(notice, encoding='utf-8-sig')
    (output / 'Candidate.iss').write_text(candidate, encoding='utf-8-sig')
    files = [{'path': p.relative_to(output / 'desktop').as_posix(), 'bytes': p.stat().st_size, 'sha256': digest(p)}
             for p in sorted((output / 'desktop').rglob('*')) if p.is_file()]
    record = {'candidateOnly': True, 'releaseFrozen': False, 'realMsiSessionValidated': False,
              'windowsInstallationTested': False, 'upgradeSupported': False, 'freshMachineOnly': True,
              'originalInnoSha256': ORIGINAL_SHA256, 'candidateInnoSha256': digest(output / 'Candidate.iss'),
              'nativeFileCount': len(native_record['files']) + 1, 'desktopFiles': files,
              'nativePayload': str(native.resolve())}
    (output / 'standalone-build-inputs.json').write_text(json.dumps(record, indent=2) + '\n')
    return record


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ['native', 'desktop', 'output']:
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    result = prepare(args.native, args.desktop, args.output)
    print(json.dumps({k: v for k, v in result.items() if k != 'desktopFiles'}))
