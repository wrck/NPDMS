"""Record first-install credentials locally without printing their values."""
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import stat
import sys


def save(directory, filename, record):
    for path in (directory, *directory.parents):
        if path.is_symlink():
            raise ValueError('Symlinked configuration path refused')
    directory.mkdir(parents=True, mode=0o700, exist_ok=True)
    if os.name == 'posix':
        info = directory.stat()
        if info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) != 0o700:
            raise ValueError('Credential directory must be owned by the operator with mode 0700')
    target = directory / filename
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w', encoding='utf-8') as output:
        json.dump(record, output, ensure_ascii=False, indent=2)
        output.write('\n')
        output.flush()
        os.fsync(output.fileno())
    return target


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('service', choices=['mysql', 'redis', 'minio', 'backend', 'dac', 'os'])
    parser.add_argument('account')
    parser.add_argument('record_id', help='Unique identifier; existing records are never overwritten')
    parser.add_argument('--state', choices=['temporary', 'configured', 'verified'], required=True)
    parser.add_argument('--mysql-init-log', type=Path)
    args = parser.parse_args()
    if os.name != 'posix' or os.geteuid() != 0:
        parser.error('Run on the target Linux host as root')
    for value in (args.account, args.record_id):
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.@-]{0,79}', value):
            parser.error('Invalid account or record ID')
    os.umask(0o077)
    if args.mysql_init_log:
        if args.service != 'mysql' or args.account != 'root' or args.state != 'temporary':
            parser.error('Initialization log only records temporary MySQL root credentials')
        lines = args.mysql_init_log.read_text(encoding='utf-8').splitlines()
        values = [line.split('root@localhost: ', 1)[1] for line in lines
                  if 'A temporary password is generated for root@localhost: ' in line]
        if len(values) != 1:
            raise ValueError('Expected exactly one generated temporary root password')
        password = values[0]
    else:
        if not sys.stdin.isatty():
            parser.error('Use an interactive terminal; do not pass passwords on the command line')
        password = getpass.getpass('Password to record (not echoed): ')
        if password != getpass.getpass('Repeat password: '):
            raise ValueError('Passwords do not match')
    if not password:
        raise ValueError('Empty credential refused')
    target = save(Path('/opt/sdp/config/credentials') / args.service,
                  args.account + '-' + args.record_id + '.json',
                  {'service': args.service, 'account': args.account, 'state': args.state,
                   'password': password})
    print(f'Credential record saved: {target} (root-only; no account was created or changed)')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError):
        raise SystemExit('Credential recording failed; check paths, permissions, input and existing record. No password printed.')
