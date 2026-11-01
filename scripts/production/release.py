"""Create/verify immutable, secret-free release directories. No server operations."""
import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path


def inventory(root):
    result = {}
    for item in sorted(root.rglob('*')):
        if item.is_symlink():
            raise ValueError('Symlinks are not permitted in release inputs')
        if item.is_file() and item != root / 'release.json':
            name = item.relative_to(root).as_posix()
            if item.suffix in ('.pem', '.key', '.p12', '.env') or item.name.startswith('.env'):
                raise ValueError(f'Credential/config file forbidden: {name}')
            result[name] = hashlib.sha256(item.read_bytes()).hexdigest()
    return result


def verify(root, role):
    manifest = json.loads((root / 'release.json').read_text(encoding='utf-8'))
    if manifest['role'] != role or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]{0,79}', manifest['release_id']):
        raise ValueError('Invalid release identity')
    files = inventory(root)
    if not files or files != manifest['files']:
        raise ValueError('Release hash or file inventory mismatch')
    required = {'backend': 'yudao-server.jar', 'frontend': 'index.html', 'dac': 'device-ops-server.jar'}
    if role in required and required[role] not in files:
        raise ValueError('Required entry point missing')
    prefixes = 'BV' if role == 'migrations' else 'V'
    if role in ('migrations', 'dac-migrations') and not any(
            re.fullmatch(r'[' + prefixes + r']\d+(?:[._]\d+)*__[^/]+\.sql', n) for n in files):
        raise ValueError('No Flyway migrations')
    return manifest


def pack(source, target, role, release_id):
    if not source.is_dir() or target.exists() or source.resolve() in target.resolve().parents:
        raise ValueError('Input must exist; destination must be new and outside source')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]{0,79}', release_id):
        raise ValueError('Invalid release ID')
    files = inventory(source)
    if (source / 'release.json').exists():
        raise ValueError('Source already contains a release manifest')
    shutil.copytree(source, target)
    (target / 'release.json').write_text(json.dumps({
        'release_id': release_id, 'role': role, 'files': files
    }, indent=2) + '\n', encoding='utf-8')
    verify(target, role)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    p = sub.add_parser('pack')
    p.add_argument('role', choices=['backend', 'frontend', 'migrations', 'dac', 'dac-migrations'])
    p.add_argument('release_id')
    p.add_argument('source', type=Path)
    p.add_argument('target', type=Path)
    p = sub.add_parser('verify')
    p.add_argument('role', choices=['backend', 'frontend', 'migrations', 'dac', 'dac-migrations'])
    p.add_argument('source', type=Path)
    args = parser.parse_args()
    if args.action == 'pack':
        pack(args.source, args.target, args.role, args.release_id)
    else:
        print(verify(args.source, args.role)['release_id'])
