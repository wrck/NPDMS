import argparse, hashlib, json, os, re, subprocess, sys, uuid
from pathlib import Path
import xml.etree.ElementTree as ET

def source(root, target):
    if target not in {'backend', 'dac'}:
        raise ValueError('Unsupported build target')
    root = Path(root).resolve()
    reactor = root if target == 'backend' else root / 'device-ops-platform'
    inputs = set()
    visited = set()

    def include(path):
        if path.is_file():
            inputs.add(path)
        elif path.is_dir():
            for directory, dirs, files in os.walk(path):
                dirs[:] = sorted(d for d in dirs if d not in {'.git', '.run', 'target', 'node_modules', '.idea'})
                inputs.update(Path(directory) / name for name in files)

    def module(directory):
        directory = directory.resolve()
        if not directory.is_relative_to(reactor):
            raise ValueError('Maven module is outside the target reactor')
        if directory in visited:
            return
        visited.add(directory)
        pom = directory / 'pom.xml'
        model = ET.parse(pom).getroot()  # A missing or invalid declared module must fail closed.
        include(pom)
        include(directory / 'src')
        include(directory / '.mvn')
        include(directory / 'lombok.config')
        for modules in model.iter():
            if modules.tag.rsplit('}', 1)[-1] == 'modules':
                for child in modules:
                    if child.tag.rsplit('}', 1)[-1] == 'module' and child.text:
                        module(directory / child.text.strip())

    module(reactor)
    include(root / '.mvn')
    include(root / 'lombok.config')
    for name in ('worktree-build.ps1', 'worktree-toolchain.ps1', 'worktree-artifact.py'):
        include(root / 'scripts' / name)
    if target == 'dac':
        # device-ops-server/pom.xml copies these assets into the executable JAR.
        include(reactor / 'device-ops-web' / 'dist')
    digest = hashlib.sha256(f'{target}-maven-reactor-v1\0'.encode())
    for path in sorted(inputs, key=lambda value: value.relative_to(root).as_posix()):
        digest.update(path.relative_to(root).as_posix().encode())
        digest.update(b'\0')
        digest.update(path.read_bytes())
        digest.update(b'\0')
    return digest.hexdigest()

def freeze(root, target, jar, revision, allow_offline=False, release_root=None):
    if release_root is not None and not Path(release_root).is_absolute():
        raise ValueError('Release root must be an absolute directory')
    subprocess.run([sys.executable,str(root/'.run/dac-host/verify-deployed-repairs.py'),target,str(jar)],check=True)
    data=jar.read_bytes()
    release=(Path(release_root) if release_root is not None else root/'.run/build/releases')/str(uuid.uuid4())
    release.mkdir(parents=True,exist_ok=False)
    artifact=release/'application.jar'
    artifact.write_bytes(data)
    artifact.chmod(0o444)
    manifest={'workspace':str(root),'target':target,'artifact':'application.jar','sha256':hashlib.sha256(data).hexdigest(),
              'source':revision,'sourceScope':f'{target}-maven-reactor-v1',
              'jdk':'25.0.1+8','maven':'3.8.6','profiles':'local,dac' if target=='backend' else 'npdms'}
    if target=='backend':
        pid_file=root/'.run/backend-domain-test.pid'
        if allow_offline and not pid_file.exists():
            runtime='__OFFLINE__'
        else:
            pid=pid_file.read_text().strip()
            if not pid.isdigit(): raise ValueError('Invalid running PID')
            command=f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={pid}'; if(!$p){{Write-Output '__OFFLINE__';exit}}; $m=[regex]::Match($p.CommandLine,'-jar\\s+(?:\"([^\"]+)\"|(\\S+))'); if(!$m.Success){{throw 'Running artifact could not be resolved'}}; if($m.Groups[1].Success){{$m.Groups[1].Value}}else{{$m.Groups[2].Value}}"
            runtime=subprocess.check_output(['powershell.exe','-NoProfile','-Command',command],text=True).strip()
        if runtime=='__OFFLINE__':
            if not allow_offline: raise ValueError('Backend is offline; explicit --allow-offline-runtime is required')
            listeners=subprocess.check_output(['netstat.exe','-ano','-p','tcp'],text=True)
            if re.search(r':59191\s+\S+\s+LISTENING\b',listeners): raise ValueError('Port 59191 is occupied by an untracked process')
            manifest['runtimeState']='offline'
            manifest['runningArtifactSha256']=None
        else:
            running=Path(runtime).resolve()
            runtime_roots = ((root/'.run').resolve(), (root/'.run/build/releases').resolve())
            if not any(running.is_relative_to(allowed) for allowed in runtime_roots):
                raise ValueError('Unexpected runtime location')
            manifest['runningArtifactSha256']=hashlib.sha256(running.read_bytes()).hexdigest()
    (release/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    print(release/'manifest.json')

if __name__=='__main__':
    mode=sys.argv[1];root=Path(sys.argv[2]).resolve()
    if mode=='source': print(source(root, sys.argv[3]))
    elif mode=='freeze':
        parser=argparse.ArgumentParser()
        parser.add_argument('--allow-offline-runtime',action='store_true')
        parser.add_argument('--release-root',type=Path)
        options=parser.parse_args(sys.argv[6:])
        freeze(root,sys.argv[3],Path(sys.argv[4]),sys.argv[5],allow_offline=options.allow_offline_runtime,release_root=options.release_root)
    elif mode in {'verify','verify-dac'}:
        manifest_path=Path(sys.argv[3]).resolve()
        manifest=json.loads(manifest_path.read_text())
        target='dac' if mode=='verify-dac' else 'backend'
        assert Path(manifest['workspace']).resolve()==root and manifest['target']==target
        assert manifest['artifact']=='application.jar' and manifest['profiles']==('npdms' if target=='dac' else 'local,dac')
        artifact=manifest_path.parent/manifest['artifact']
        assert hashlib.sha256(artifact.read_bytes()).hexdigest()==manifest['sha256'],'Frozen artifact changed'
        subprocess.run([sys.executable,str(root/'.run/dac-host/verify-deployed-repairs.py'),target,str(artifact)],check=True)
        print(artifact)
    else: raise SystemExit('Unsupported operation')
