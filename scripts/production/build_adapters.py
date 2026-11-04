"""Compile copied deployment adapters against the exact frozen application JARs.

Invoke while holding the worktree build lock, with the pinned JDK. No Maven
target directory or original application archive is modified.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile


def compile_adapter(jdk, application, output, names):
    output.mkdir(parents=True, exist_ok=False)
    libraries = output / 'libraries'
    classes = output / 'classes'
    libraries.mkdir()
    classes.mkdir()
    with zipfile.ZipFile(application) as archive:
        if 'org/springframework/boot/loader/launch/PropertiesLauncher.class' not in archive.namelist():
            raise ValueError('Application does not support external deployment adapters')
        for entry in archive.infolist():
            if entry.filename.startswith('BOOT-INF/lib/') and entry.filename.endswith('.jar'):
                (libraries / Path(entry.filename).name).write_bytes(archive.read(entry))
            elif entry.filename.startswith('BOOT-INF/classes/') and not entry.is_dir():
                relative = Path(entry.filename.removeprefix('BOOT-INF/classes/'))
                if relative.is_absolute() or '..' in relative.parts:
                    raise ValueError('Unsafe class path')
                target = output / 'application-classes' / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(entry))
    sources = [Path(__file__).parent / 'adapters' / name for name in names]
    classpath = os.pathsep.join([str(output / 'application-classes'), str(libraries / '*')])
    suffix = '.exe' if os.name == 'nt' else ''
    subprocess.run([str(jdk / 'bin' / ('javac' + suffix)), '-encoding', 'UTF-8', '-cp', classpath,
                    '-d', str(classes), *map(str, sources)], check=True)
    target = output / 'deployment-adapter.jar'
    subprocess.run([str(jdk / 'bin' / ('jar' + suffix)), '--create', '--file', str(target),
                    '-C', str(classes), '.'], check=True)
    (output / 'source.json').write_text(json.dumps({
        'application': str(application),
        'applicationSha256': hashlib.sha256(application.read_bytes()).hexdigest(),
        'sources': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
        'adapterSha256': hashlib.sha256(target.read_bytes()).hexdigest(),
    }, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=['backend', 'dac'])
    parser.add_argument('jdk', type=Path)
    parser.add_argument('application', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    names = ['BackendDacIdentityConfiguration.java', 'ServerSdpStorageDeploymentConfiguration.java'] if args.kind == 'backend' else ['ServerDacDeploymentConfiguration.java']
    compile_adapter(args.jdk, args.application.resolve(), args.output.resolve(), names)
