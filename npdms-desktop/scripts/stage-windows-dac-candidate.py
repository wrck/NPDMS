"""Stage existing compiled inputs for Windows validation, never a frozen MSI release.

No service runs and no installation/configuration/ACL writes occur. The original
package-dac-desktop.ps1 remains authoritative for native installer validation.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import zipfile


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser()
    for name in ['jar', 'native-build', 'native-record', 'jre-zip', 'jre-checksum', 'winsw', 'winsw-license', 'output']:
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    native_source = repo / 'device-ops-platform/packaging/windows'
    expected = re.match(r'^([a-fA-F0-9]{64})(?:\s|$)', args.jre_checksum.read_text())
    if not expected or digest(args.jre_zip) != expected[1].lower():
        raise ValueError('Official Windows JRE checksum mismatch')
    if digest(args.winsw) != 'b5066b7bbdfba1293e5d15cda3caaea88fbeab35bd5b38c41c913d492aadfc4f':
        raise ValueError('Pinned official-download WinSW 2.12.0 bytes differ')
    record = json.loads(args.native_record.read_text())
    for name in ['SdpDac', 'SdpDacHost']:
        item = next(entry for entry in record['results'] if entry['name'] == name)
        if item['exitCode'] != 0 or digest(args.native_build / (name + '.exe')) != item['sha256']:
            raise ValueError('Native compiled artifact mismatch')
        for filename, sha256 in item['sourceHashes'].items():
            if digest(native_source / filename) != sha256:
                raise ValueError('Native compiled source no longer matches')
    with zipfile.ZipFile(args.jar) as jar:
        if jar.testzip() is not None or 'BOOT-INF/classes/application-desktop.yml' not in jar.namelist():
            raise ValueError('Invalid desktop JAR')
    args.output.mkdir(parents=True, exist_ok=False)
    out = args.output
    with zipfile.ZipFile(args.jre_zip) as archive:
        if archive.testzip() is not None:
            raise ValueError('JRE archive CRC failed')
        roots = set()
        for entry in archive.infolist():
            parts = PurePosixPath(entry.filename).parts
            if not parts or entry.filename.startswith('/') or '\\' in entry.filename or any(p in ['..', '.'] or ':' in p for p in parts):
                raise ValueError('Unsafe archive path')
            roots.add(parts[0])
            if len(roots) != 1 or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError('Unexpected JRE archive root/link')
            target = out / 'runtime' / Path(*parts[1:])
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(entry) as source, target.open('xb') as destination:
                    shutil.copyfileobj(source, destination)
    release = (out / 'runtime/release').read_text()
    if not all(value in release for value in ['OS_NAME="Windows"', 'OS_ARCH="x86_64"', 'JAVA_RUNTIME_VERSION="25.0.1+8-LTS"', 'jdk.crypto.mscapi']):
        raise ValueError('Pinned Windows runtime mismatch')
    java = (out / 'runtime/bin/java.exe').read_bytes()
    pe = struct.unpack_from('<I', java, 60)[0]
    if java[pe:pe+4] != b'PE\0\0' or struct.unpack_from('<H', java, pe+4)[0] != 0x8664:
        raise ValueError('Windows runtime is not x64 PE')
    (out / 'app').mkdir()
    shutil.copyfile(args.jar, out / 'app/device-ops-server.jar')
    for name in ['SdpDac.exe', 'SdpDacHost.exe']:
        shutil.copyfile(args.native_build / name, out / name)
    shutil.copyfile(args.winsw, out / 'SdpDacService.exe')
    shutil.copyfile(args.winsw_license, out / 'WinSW-LICENSE.txt')
    shutil.copyfile(native_source / 'SdpDacService.xml', out / 'SdpDacService.xml')
    shutil.copytree(repo / 'device-ops-platform/parser-releases/device-command-output-1.4.0', out / 'parser-release')
    (out / 'CANDIDATE-NOT-INSTALLER.txt').write_text(
        'Windows 原生载荷候选，仅用于继续安装链验证。未安装服务，未完成正式冻结或 Windows ACL/DPAPI/MSI 验收。\n'
        '这是组件目录，不是安装器，不要手工覆盖已有安装。保留原始数据与密钥。\n', encoding='utf-8')
    files = [{'path': str(p.relative_to(out)), 'bytes': p.stat().st_size, 'sha256': digest(p)}
             for p in sorted(out.rglob('*')) if p.is_file()]
    result = {'candidateOnly': True, 'releaseFrozen': False, 'installed': False, 'windowsExecutionTested': False,
              'jreOfficialChecksumVerified': expected[1].lower(), 'jarSha256': digest(args.jar),
              'winswSha256': digest(args.winsw), 'winswUpstreamSeparateChecksumVerified': False,
              'nativeSourcesVerified': True, 'fileCount': len(files), 'files': files}
    (out / 'candidate-inputs.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key != 'files'}))


if __name__ == '__main__':
    main()
