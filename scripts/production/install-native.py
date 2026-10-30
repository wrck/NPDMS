"""Install approved native binary archives without starting services (Python 3.12+)."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile
import tempfile

ENTRIES = {
    'mysql': ('bin/mysqld', 'bin/mysql', 'bin/mysqldump'),
    'redis': ('bin/redis-server', 'bin/redis-cli'),
    'minio': ('bin/minio',),
    'flyway': ('flyway',),
    'jdk': ('bin/java',),
}


def unpack(archive, destination, component, digest):
    if not re.fullmatch('[a-f0-9]{64}', digest):
        raise ValueError('Approved SHA256 required')
    with archive.open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != digest:
        raise ValueError('Archive SHA256 mismatch')
    with tarfile.open(archive) as bundle:
        for member in bundle.getmembers():
            path = PurePosixPath(member.name)
            if path.is_absolute() or '..' in path.parts:
                raise ValueError('Unsafe archive member path')
        # Python data filter rejects escaping links, absolute paths, devices, etc.
        bundle.extractall(destination, filter='data')
    children = list(destination.iterdir())
    root = destination
    if not all((root / entry).is_file() for entry in ENTRIES[component]):
        if len(children) == 1 and children[0].is_dir() and not children[0].is_symlink():
            root = children[0]
    for relative in ENTRIES[component]:
        entry = root / relative
        if not entry.is_file() or root.resolve() not in entry.resolve().parents:
            raise ValueError(f'Missing/escaping native executable: {relative}')
    return root


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('component', choices=ENTRIES)
    parser.add_argument('version', help='Approved immutable software version')
    parser.add_argument('archive', type=Path)
    parser.add_argument('sha256')
    parser.add_argument('--approved', action='store_true')
    args = parser.parse_args()
    if not args.approved or os.name != 'posix' or os.geteuid() != 0:
        parser.error('Approved root operation on target Linux host required')
    if not re.fullmatch('[A-Za-z0-9][A-Za-z0-9._-]{0,79}', args.version):
        parser.error('Invalid version')
    expected = '172.17.74.19' if args.component == 'mysql' else '172.17.73.104'
    addresses = json.loads(subprocess.check_output(['ip', '-j', '-4', 'addr', 'show']))
    if not any(a.get('local') == expected for interface in addresses for a in interface.get('addr_info', [])):
        parser.error('Wrong target host')
    import fcntl
    with open('/run/lock/npdms-production.lock', 'w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        base = Path('/opt/sdp')
        target = base / 'software' / (args.component + '-' + args.version)
        link = base / args.component
        if target.exists() or target.is_symlink() or link.exists() or link.is_symlink():
            parser.error('Existing installation preserved; upgrades require a separate reviewed switch')
        target.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='.native-', dir=base) as temporary:
            root = unpack(args.archive.resolve(), Path(temporary), args.component, args.sha256)
            for relative in ENTRIES[args.component]:
                (root / relative).chmod(0o755)
            shutil.move(str(root), str(target))
        link.symlink_to(target, target_is_directory=True)
        print(f'Installed {link} -> {target}; no service initialized or started')


if __name__ == '__main__':
    main()
