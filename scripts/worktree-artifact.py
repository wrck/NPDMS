import hashlib, json, os, subprocess, sys, uuid
from pathlib import Path

def source(root):
    digest=hashlib.sha256()
    for directory, dirs, files in os.walk(root):
        dirs[:]=sorted(d for d in dirs if d not in {'.git','.run','target','node_modules','dist','.idea'})
        for name in sorted(files):
            path=Path(directory)/name
            if path.suffix not in {'.java','.xml','.yaml','.yml','.properties','.json','.ps1','.py','.config'}: continue
            digest.update(path.relative_to(root).as_posix().encode())
            digest.update(path.read_bytes())
    return digest.hexdigest()

def freeze(root, target, jar, revision):
    subprocess.run([sys.executable,str(root/'.run/dac-host/verify-deployed-repairs.py'),target,str(jar)],check=True)
    data=jar.read_bytes()
    release=root/'.run/build/releases'/str(uuid.uuid4())
    release.mkdir(parents=True,exist_ok=False)
    artifact=release/'application.jar'
    artifact.write_bytes(data)
    artifact.chmod(0o444)
    manifest={'workspace':str(root),'target':target,'artifact':'application.jar','sha256':hashlib.sha256(data).hexdigest(),
              'source':revision,'jdk':'25.0.1+8','maven':'3.8.6','profiles':'local,dac' if target=='backend' else 'npdms'}
    if target=='backend':
        pid=(root/'.run/backend-domain-test.pid').read_text().strip()
        if not pid.isdigit(): raise ValueError('Invalid running PID')
        command=f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={pid}'; $m=[regex]::Match($p.CommandLine,'-jar\\s+(?:\"([^\"]+)\"|(\\S+))'); if($m.Groups[1].Success){{$m.Groups[1].Value}}else{{$m.Groups[2].Value}}"
        running=Path(subprocess.check_output(['powershell.exe','-NoProfile','-Command',command],text=True).strip()).resolve()
        if not running.is_relative_to(root/'.run'): raise ValueError('Unexpected runtime location')
        manifest['runningArtifactSha256']=hashlib.sha256(running.read_bytes()).hexdigest()
    (release/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    print(release/'manifest.json')

if __name__=='__main__':
    mode=sys.argv[1];root=Path(sys.argv[2]).resolve()
    if mode=='source': print(source(root))
    elif mode=='freeze': freeze(root,sys.argv[3],Path(sys.argv[4]),sys.argv[5])
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
