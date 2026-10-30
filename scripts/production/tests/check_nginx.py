"""Optional native Linux Nginx test: local TLS stub, no containers or production access."""
import json
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from render import render


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def main():
    nginx = shutil.which('nginx')
    if not nginx or not Path('/dev/shm').is_dir():
        raise SystemExit('Native Linux nginx, curl, openssl and /dev/shm required')
    config = json.loads((Path(__file__).resolve().parents[1] / 'site.example.json').read_text())
    for key in ('internal_domain', 'external_domain', 'backend_tls_name', 'db_tls_name'):
        config[key] = key.replace('_', '-') + '.example.test'
    front, back = free_port(), free_port()
    while front == back:
        back = free_port()
    with tempfile.TemporaryDirectory(prefix='npdms-proxy-', dir='/dev/shm') as tmp:
        root = Path(tmp)
        root.chmod(0o755)  # Nginx workers must traverse the synthetic static fixture.
        render(config, root / 'rendered')
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
            '-keyout', str(root / 'key.pem'), '-out', str(root / 'cert.pem'), '-subj', '/CN=backend-tls-name.example.test',
            '-addext', 'subjectAltName=DNS:backend-tls-name.example.test,DNS:external-domain.example.test'],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        (root / 'static/assets').mkdir(parents=True)
        (root / 'static/index.html').write_text('TEST_PAGE')
        (root / 'static/assets/test.js').write_text('TEST_ASSET')
        replacements = {
            '/run/sdp-nginx/body': str(root / 'body-temp'),
            '/run/sdp-nginx/proxy': str(root / 'proxy-temp'),
            'listen 443 ssl;': f'listen 127.0.0.1:{front} ssl;', '172.17.73.104:443': f'127.0.0.1:{back}',
            '/opt/sdp/frontend/current': str(root / 'static'),
            '/etc/sdp/tls/frontend.crt': str(root / 'cert.pem'),
            '/etc/sdp/tls/backend-ca.crt': str(root / 'cert.pem'),
            '/run/sdp-secrets/frontend.key': str(root / 'key.pem'),
            '/etc/sdp/customer-proxy.conf': str(root / 'customer-proxy.conf'),
            '/var/log/nginx/npdms-access.log': str(root / 'access.log'),
        }
        for name in ('fe1/sdp.conf', 'fe2/sdp.conf', 'fe2/customer-proxy.conf'):
            content = (root / 'rendered' / name).read_text()
            for before, after in replacements.items():
                content = content.replace(before, after)
            output = name.split('/')[0] + '.conf' if name.endswith('/sdp.conf') else 'customer-proxy.conf'
            (root / output).write_text(content)
        for role in ('fe1', 'fe2'):
            (root / ('main-' + role + '.conf')).write_text(
                f'pid {root}/nginx.pid; error_log {root}/error.log; events {{}} http {{ '
                f'client_body_temp_path {root}/body-temp; proxy_temp_path {root}/proxy-temp; '
                f'fastcgi_temp_path {root}/fcgi-temp; uwsgi_temp_path {root}/uwsgi-temp; scgi_temp_path {root}/scgi-temp; '
                f'include {root}/{role}.conf; server {{ listen 127.0.0.1:{back} ssl; '
                f'ssl_certificate {root}/cert.pem; ssl_certificate_key {root}/key.pem; access_log off; '
                'return 200 "$request_method $request_uri auth=$http_authorization cookie=$http_cookie tenant=$http_tenant_id"; } }')
            subprocess.run([nginx, '-p', str(root), '-c', str(root / ('main-' + role + '.conf')), '-t'], check=True)
        process = subprocess.Popen([nginx, '-p', str(root), '-c', str(root / 'main-fe2.conf'), '-g', 'daemon off;'])
        try:
            for _ in range(50):
                if process.poll() is not None:
                    raise RuntimeError('Test Nginx failed to start')
                try:
                    with socket.create_connection(('127.0.0.1', front), timeout=.1):
                        break
                except OSError:
                    time.sleep(.1)
            api = '/admin-api/api/v1/pms/'
            cases = [('GET', '/satisfaction-questionnaires/test-token?tenantId=17', 200),
                     ('GET', '/training-records/test-token?tenantId=17', 200), ('GET', '/assets/test.js', 200),
                     ('GET', api + 'satisfaction-questionnaires/test-token', 200)]
            cases += [('POST', api + 'satisfaction-questionnaires/test-token/' + x, 200)
                      for x in ('responses', 'files/initialize', 'files/123/complete')]
            cases += [('POST', api + 'training-records/test-token/confirm', 200),
                      ('GET', api + 'satisfaction-questionnaires/test-token/responses', 403),
                      ('POST', api + 'training-records/test-token', 403)]
            cases += [('GET', x, 404) for x in ('/login', api + 'projects', '/actuator/health', '/swagger-ui',
                      api + 'training-records/123/pdf', '/assets/test.js.map', '/.env', '/index.html')]
            cases += [('POST', '/admin-api/system/auth/login', 404)]
            for method, path, expected in cases:
                code = subprocess.check_output(['curl', '--noproxy', '*', '--silent', '--show-error',
                    '--cacert', str(root / 'cert.pem'), '--resolve', f'external-domain.example.test:{front}:127.0.0.1',
                    '-X', method, '-H', 'Authorization: Bearer fixture', '-H', 'Cookie: fixture=1', '-H', 'tenant-id: 17',
                    '-o', str(root / 'body'), '-w', '%{http_code}', f'https://external-domain.example.test:{front}{path}'], text=True)
                assert code == str(expected), (method, path, code, expected)
                if path == api + 'training-records/test-token/confirm':
                    assert (root / 'body').read_text() == f'POST {path} auth= cookie= tenant=17'
                print('PASS', method, path, code)
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=15)


if __name__ == '__main__':
    main()
