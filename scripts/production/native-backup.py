"""Native production backup using the existing host-encrypted backup-key workflow.

Never removes previous backups. Verification decrypts and reads every byte, without
restoring into a live database. Off-host replication and key escrow are separate.
"""
import argparse
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import time


def checked(args, data=None):
    result = subprocess.run(args, input=data, capture_output=True)
    if result.returncode:
        raise RuntimeError("Backup subprocess failed: " + Path(args[0]).name)
    return result.stdout


def memory_file(name, value):
    fd = os.memfd_create(name, 0)
    os.fchmod(fd, 0o600)
    os.write(fd, value)
    os.lseek(fd, 0, 0)
    return fd


def verify(directory):
    receipt = json.loads((directory / 'receipt.json').read_text())
    key = checked(['systemd-creds', 'decrypt', '--name=sdp-backup-key',
                   str(directory / 'backup-key.cred'), '-'])
    key_fd = memory_file('backup-verify-key', key + b'\n')
    try:
        for item in receipt['archives']:
            archive = directory / item['file']
            with archive.open('rb') as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != item['sha256']:
                    raise RuntimeError('Encrypted archive checksum mismatch')
            os.lseek(key_fd, 0, 0)
            process = subprocess.Popen(['openssl', 'enc', '-d', '-aes-256-cbc', '-pbkdf2',
                '-iter', '200000', '-pass', 'fd:' + str(key_fd), '-in', str(archive)],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, pass_fds=(key_fd,))
            digest = hashlib.sha256()
            count = 0
            try:
                with gzip.GzipFile(fileobj=process.stdout) as stream:
                    while block := stream.read(1024 * 1024):
                        digest.update(block)
                        count += len(block)
                if process.wait() or count != item['plainBytes'] or digest.hexdigest() != item['plainSha256']:
                    raise RuntimeError('Decrypted archive verification failed')
            finally:
                if process.poll() is None:
                    process.terminate()
                    process.wait()
    finally:
        os.close(key_fd)
    return receipt


def archive_stream(directory, name, command, key_fd, pass_fds=()):
    target = directory / name
    digest = hashlib.sha256()
    count = 0
    os.lseek(key_fd, 0, 0)
    with (directory / (name + '.stderr')).open('wb') as errors:
        source = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=errors, pass_fds=pass_fds)
        cipher = subprocess.Popen(['openssl', 'enc', '-aes-256-cbc', '-pbkdf2', '-iter', '200000',
            '-salt', '-pass', 'fd:' + str(key_fd), '-out', str(target)],
            stdin=subprocess.PIPE, stderr=subprocess.PIPE, pass_fds=(key_fd,))
        try:
            with gzip.GzipFile(fileobj=cipher.stdin, mode='wb', compresslevel=1, mtime=0) as compressed:
                while block := source.stdout.read(1024 * 1024):
                    digest.update(block)
                    count += len(block)
                    compressed.write(block)
            cipher.stdin.close()
            if source.wait() or cipher.wait():
                raise RuntimeError('Archive generation failed; incomplete directory retained')
        finally:
            for process in (source, cipher):
                if process.poll() is None:
                    process.terminate()
                    process.wait()
    with target.open('rb') as stream:
        encrypted_digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return dict(file=name, bytes=target.stat().st_size, sha256=encrypted_digest,
                plainBytes=count, plainSha256=digest.hexdigest())


def redis_snapshot():
    # Credentials stay in memory; the generated RDB is the normal Redis snapshot.
    import shlex
    effective = {}
    for line in checked(['systemctl', 'cat', 'sdp']).decode().splitlines():
        if line == 'LoadCredentialEncrypted=':
            effective.clear()
        elif line.startswith('LoadCredentialEncrypted='):
            entry = shlex.split(line.split('=', 1)[1])
            if len(entry) != 1: raise RuntimeError('Unsupported credential declaration')
            name, source = entry[0].split(':', 1)
            effective[name] = source
    source = effective.get('application-secrets.properties')
    if not source:
        raise RuntimeError('Cannot resolve the effective backup credential source')
    properties = checked(['systemd-creds', 'decrypt', '--name=application-secrets.properties', source, '-'])
    raw = checked(['/opt/sdp/jdk/bin/java', '/opt/sdp/tools/BackupRuntimeSecrets.java'], properties)
    import base64
    password = base64.b64decode(raw.strip()).decode()
    with socket.create_connection(('127.0.0.1', 6379), timeout=10) as connection:
        reader = connection.makefile('rb')

        def command(*args):
            parts = [str(v).encode() for v in args]
            connection.sendall(b'*' + str(len(parts)).encode() + b'\r\n' + b''.join(
                b'$' + str(len(v)).encode() + b'\r\n' + v + b'\r\n' for v in parts))
            head = reader.readline()
            if head[:1] == b'-':
                raise RuntimeError('Redis snapshot command rejected')
            if head[:1] == b'$':
                length = int(head[1:]); data = reader.read(length); reader.read(2)
                return data.decode()
            return head[1:].decode().strip()

        command('AUTH', password)
        def info():
            return dict(line.split(':', 1) for line in command('INFO', 'persistence').splitlines() if ':' in line)
        deadline = time.monotonic() + 120
        while info()['rdb_bgsave_in_progress'] != '0':
            if time.monotonic() > deadline: raise RuntimeError('Existing Redis snapshot timed out')
            time.sleep(1)
        command('BGSAVE')
        while info()['rdb_bgsave_in_progress'] != '0':
            if time.monotonic() > deadline: raise RuntimeError('Redis snapshot timed out')
            time.sleep(1)
        if info()['rdb_last_bgsave_status'] != 'ok':
            raise RuntimeError('Redis snapshot failed')


def backup(role):
    expected = {'db': 'SDP-DB', 'be': 'SDP-BE'}[role]
    if socket.gethostname() != expected or not os.path.ismount('/data'):
        raise RuntimeError('Unexpected host or data mount')
    if os.statvfs('/data').f_bavail * os.statvfs('/data').f_frsize < 30 * 1024**3:
        raise RuntimeError('Less than 30 GiB available; keep existing backups and stop')
    directory = Path('/data/backups/daily') / time.strftime('%Y%m%dT%H%M%SZ', time.gmtime())
    directory.mkdir(parents=True, exist_ok=False)
    key = secrets.token_urlsafe(48).encode()
    checked(['systemd-creds', 'encrypt', '--name=sdp-backup-key', '-', str(directory / 'backup-key.cred')], key)
    if checked(['systemd-creds', 'decrypt', '--name=sdp-backup-key', str(directory / 'backup-key.cred'), '-']) != key:
        raise RuntimeError('Backup key verification failed')
    key_fd = memory_file('backup-key', key + b'\n')
    archives = []
    started = time.time()
    try:
        if role == 'db':
            credentials = json.loads(Path('/opt/sdp/config/credentials/mysql/root-verified-20261030.json').read_text())
            sock = re.search(r'^socket\s*=\s*(.+)$', Path('/etc/sdp/mysql.cnf').read_text(), re.M).group(1).strip()
            password = credentials['password'].replace('\\', '\\\\').replace('"', '\\"')
            options = memory_file('backup-client', ('[client]\nuser=root\npassword="' + password + '"\nsocket=' + sock + '\n').encode())
            try:
                archives.append(archive_stream(directory, 'databases.sql.gz.enc', [
                    '/opt/sdp/mysql/bin/mysqldump', '--defaults-extra-file=/proc/self/fd/' + str(options),
                    '--single-transaction', '--quick', '--routines', '--triggers', '--events', '--hex-blob',
                    '--no-tablespaces', '--set-gtid-purged=OFF', '--default-character-set=utf8mb4',
                    '--databases', 'npdms', 'device_ops'], key_fd, (options,)))
            finally:
                os.close(options)
        else:
            redis_snapshot()
        paths = ['etc/sdp', 'etc/credstore.encrypted', 'var/lib/systemd/credential.secret', 'opt/sdp/config']
        paths += [str(p).lstrip('/') for p in Path('/etc/systemd/system').glob('sdp*')]
        if role == 'be': paths += ['data/minio', 'data/dac', 'data/redis/dump.rdb']
        archives.append(archive_stream(directory, 'configuration-and-files.tar.gz.enc',
            ['tar', '--acls', '--xattrs', '-C', '/', '-cf', '-', *paths], key_fd))
    finally:
        os.close(key_fd)
    receipt = dict(host=expected, capturedAt=time.strftime('%Y-%m-%dT%H:%M:%S%z'), archives=archives,
                   elapsedSeconds=round(time.time()-started, 1), verified=False,
                   restorePerformed=False, offHostReplication=False)
    (directory / 'receipt.json').write_text(json.dumps(receipt, indent=2))
    verify(directory)
    receipt['verified'] = True
    (directory / 'receipt.json').write_text(json.dumps(receipt, indent=2))
    print(json.dumps({'directory': str(directory), **receipt}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('role', choices=['be', 'db', 'verify'])
    parser.add_argument('--directory', type=Path)
    args = parser.parse_args()
    os.umask(0o077)
    if os.geteuid() != 0: raise SystemExit('Run as root')
    if args.role == 'verify':
        verify(args.directory)
        print('All encrypted archives verified; no restore performed')
    else:
        with open('/run/lock/npdms-production-backup.lock', 'w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            backup(args.role)
