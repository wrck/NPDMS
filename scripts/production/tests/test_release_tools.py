import importlib.util
import json
import tempfile
import io
import hashlib
import tarfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


release = module('release')
renderer = module('render')
native = module('install-native')
credentials = module('record-credential')


class CredentialRecordTests(unittest.TestCase):
    def test_record_preserved_and_existing_file_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / 'mysql'
            record = {'account': 'root', 'password': 'TEST-ONLY-NOT-A-REAL-SECRET', 'state': 'temporary'}
            path = credentials.save(directory, 'root-initial.json', record)
            self.assertEqual(json.loads(path.read_text(encoding='utf-8')), record)
            with self.assertRaises(FileExistsError):
                credentials.save(directory, 'root-initial.json', {'password': 'replacement'})
            self.assertEqual(json.loads(path.read_text(encoding='utf-8')), record)


class NativeInstallTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def archive(self, names):
        target = self.root / 'native.tar'
        with tarfile.open(target, 'w') as tar:
            for name in names:
                entry = tarfile.TarInfo(name)
                entry.size = 4
                tar.addfile(entry, io.BytesIO(b'test'))
        return target, hashlib.sha256(target.read_bytes()).hexdigest()

    def test_flat_native_layout_and_wrapper(self):
        for prefix in ('', 'redis-7.4/'):
            with self.subTest(prefix=prefix):
                archive, digest = self.archive([prefix + x for x in native.ENTRIES['redis']])
                destination = self.root / ('flat' if not prefix else 'wrapped')
                destination.mkdir()
                result = native.unpack(archive, destination, 'redis', digest)
                self.assertTrue((result / 'bin/redis-server').is_file())

    def test_bad_digest_and_missing_binary(self):
        archive, digest = self.archive(['README'])
        with self.assertRaises(ValueError):
            native.unpack(archive, self.root / 'bad', 'redis', '0' * 64)
        with self.assertRaises(ValueError):
            native.unpack(archive, self.root / 'missing', 'redis', digest)

    def test_traversal_rejected(self):
        archive, digest = self.archive(['../escape'])
        with self.assertRaises((tarfile.FilterError, ValueError)):
            native.unpack(archive, self.root / 'safe', 'redis', digest)
        self.assertFalse((self.root / 'escape').exists())


class ReleaseSafetyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        (self.source / 'index.html').write_text('approved', encoding='utf-8')
        self.target = self.root / 'release'

    def freeze(self):
        release.pack(self.source, self.target, 'frontend', 'R1')

    def test_valid_release_and_wrong_role(self):
        self.freeze()
        self.assertEqual(release.verify(self.target, 'frontend')['release_id'], 'R1')
        with self.assertRaises(ValueError):
            release.verify(self.target, 'backend')

    def test_changed_file_rejected(self):
        self.freeze()
        (self.target / 'index.html').write_text('tampered', encoding='utf-8')
        with self.assertRaises(ValueError):
            release.verify(self.target, 'frontend')

    def test_fresh_baseline_release_and_checksum_drift(self):
        baseline = 'B530__fresh_database.sql'
        (self.source / baseline).write_text('CREATE TABLE sample(id INT);', encoding='utf-8')
        release.pack(self.source, self.target, 'migrations', 'Fresh530')
        self.assertEqual(release.verify(self.target, 'migrations')['release_id'], 'Fresh530')
        (self.target / baseline).write_text('CREATE TABLE different(id INT);', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'hash or file inventory mismatch'):
            release.verify(self.target, 'migrations')

    def test_added_nested_manifest_rejected(self):
        self.freeze()
        (self.target / 'assets').mkdir()
        (self.target / 'assets/release.json').write_text('{}', encoding='utf-8')
        with self.assertRaises(ValueError):
            release.verify(self.target, 'frontend')

    def test_missing_file_rejected(self):
        self.freeze()
        (self.target / 'index.html').unlink()
        with self.assertRaises(ValueError):
            release.verify(self.target, 'frontend')

    def test_no_overwrite_or_path_traversal(self):
        self.freeze()
        with self.assertRaises(ValueError):
            self.freeze()
        with self.assertRaises(ValueError):
            release.pack(self.source, self.root / 'other', 'frontend', '../escape')

    def test_secret_file_rejected(self):
        (self.source / '.env.prod').write_text('fixture', encoding='utf-8')
        with self.assertRaises(ValueError):
            self.freeze()

    def test_source_nested_output_rejected(self):
        with self.assertRaises(ValueError):
            release.pack(self.source, self.source / 'nested', 'frontend', 'R1')

    def test_dac_requires_own_jar(self):
        with self.assertRaises(ValueError):
            release.pack(self.source, self.target, 'dac', 'R1')

    def test_dac_release_and_migration_isolation(self):
        (self.source / 'index.html').unlink()
        (self.source / 'device-ops-server.jar').write_bytes(b'isolated-dac-fixture')
        release.pack(self.source, self.target, 'dac', 'R1')
        self.assertEqual(release.verify(self.target, 'dac')['role'], 'dac')
        with self.assertRaises(ValueError):
            release.verify(self.target, 'backend')
        migrations = self.root / 'sql'
        migrations.mkdir()
        (migrations / 'V1__fixture.sql').write_text('SELECT 1;', encoding='utf-8')
        release.pack(migrations, self.root / 'dac-sql', 'dac-migrations', 'R1')
        with self.assertRaises(ValueError):
            release.verify(self.root / 'dac-sql', 'migrations')

    def test_missing_entry_point_rejected(self):
        (self.source / 'index.html').unlink()
        (self.source / 'wrong.txt').write_text('fixture', encoding='utf-8')
        with self.assertRaises(ValueError):
            self.freeze()

    def test_platform_baseline_only_release_is_supported(self):
        source = self.root / 'baseline'
        source.mkdir()
        (source / 'B530__production.sql').write_text('SELECT 1;', encoding='utf8')
        release.pack(source, self.root / 'platform-sql', 'migrations', 'R1')
        with self.assertRaises(ValueError):
            release.pack(source, self.root / 'dac-baseline', 'dac-migrations', 'R1')


class RenderSafetyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.target = Path(self.temp.name) / 'config'
        self.config = json.loads((ROOT / 'site.example.json').read_text(encoding='utf-8'))
        for key in ('internal_domain', 'external_domain', 'backend_tls_name', 'db_tls_name'):
            self.config[key] = key.replace('_', '-') + '.example.test'

    def test_render_complete_and_no_overwrite(self):
        renderer.render(self.config, self.target)
        self.assertTrue((self.target / 'fe2/sdp.conf').is_file())
        for file in self.target.rglob('*'):
            if file.is_file():
                self.assertNotIn('@@', file.read_text(encoding='utf-8'))
        with self.assertRaises(ValueError):
            renderer.render(self.config, self.target)

    def test_data_paths_and_mount_protection(self):
        renderer.render(self.config, self.target)
        for file in self.target.rglob('*'):
            if file.is_file():
                content = file.read_text(encoding='utf-8')
                self.assertNotIn('/srv/npdms', content)
                self.assertNotIn('/var/lib/npdms', content)
                if file.suffix == '.service':
                    self.assertIn('ConditionPathIsMountPoint=/data', content)
                    self.assertNotIn('StateDirectory=', content)
        self.assertIn('log-bin=/data/mysql/mysql-bin', (self.target / 'db/mysql.cnf').read_text())
        for name, directory in [('sdp.service', 'backend'), ('sdp-dac.service', 'dac')]:
            content = (self.target / 'be' / name).read_text()
            self.assertNotIn('-Djava.io.tmpdir=', content)
            self.assertNotIn('BindPaths=', content)

    def test_nginx_injection_rejected_before_output(self):
        self.config['external_domain'] = 'host; include /tmp/evil;'
        with self.assertRaises(ValueError):
            renderer.render(self.config, self.target)
        self.assertFalse(self.target.exists())

    def test_obsolete_container_parameter_rejected(self):
        self.config['mysql_image'] = 'mysql:latest'
        with self.assertRaises(ValueError):
            renderer.render(self.config, self.target)

    def test_unfilled_template_rejected(self):
        config = json.loads((ROOT / 'site.example.json').read_text(encoding='utf-8'))
        with self.assertRaises(ValueError):
            renderer.render(config, self.target)


if __name__ == '__main__':
    unittest.main()
