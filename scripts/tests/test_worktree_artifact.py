"""Target source guards must reject relevant changes without coupling independent builds."""
import importlib.util
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('worktree_artifact', Path(__file__).parents[1] / 'worktree-artifact.py')
artifact = importlib.util.module_from_spec(spec)
spec.loader.exec_module(artifact)


class SourceScopeTest(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)
        self.write('pom.xml', '<project><modules><module>platform</module><module>shared</module></modules></project>')
        self.write('platform/pom.xml', '<project/>')
        self.write('platform/src/main/java/Approval.java', 'approval')
        self.write('platform/src/main/resources/application.yaml', 'profile: local,dac')
        self.write('shared/pom.xml', '<project/>')
        self.write('shared/src/main/java/Common.java', 'shared')
        self.write('device-ops-platform/pom.xml', '<project><modules><module>core</module></modules></project>')
        self.write('device-ops-platform/core/pom.xml', '<project/>')
        self.write('device-ops-platform/core/src/main/java/Parser.java', 'parser')
        self.write('device-ops-platform/device-ops-web/dist/index.html', 'DAC desktop')
        self.write('yudao-ui/package.json', '{"version":"1"}')
        self.write('.mvn/maven.config', '-Dmaven.compiler.proc=full')
        self.write('scripts/worktree-build.ps1', 'pinned build')
        self.write('scripts/worktree-toolchain.ps1', 'pinned tools')
        self.write('scripts/worktree-artifact.py', 'source guard')

    def write(self, path, value):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(value, encoding='utf8')

    def source(self, target):
        return artifact.source(self.root, target)

    def test_dac_source_pom_and_packaged_web_assets_do_not_block_backend(self):
        for path in ('device-ops-platform/core/src/main/java/Parser.java',
                     'device-ops-platform/core/pom.xml',
                     'device-ops-platform/device-ops-web/dist/assets/app.js'):
            with self.subTest(path=path):
                before_backend, before_dac = self.source('backend'), self.source('dac')
                self.write(path, '<project><!--changed--></project>' if path.endswith('pom.xml') else 'changed')
                self.assertEqual(before_backend, self.source('backend'))
                self.assertNotEqual(before_dac, self.source('dac'))

    def test_backend_and_shared_module_changes_still_block_backend_only(self):
        for path in ('platform/src/main/java/Approval.java',
                     'platform/src/main/resources/application.yaml',
                     'shared/src/main/java/Common.java'):
            with self.subTest(path=path):
                before_backend, before_dac = self.source('backend'), self.source('dac')
                self.write(path, 'changed')
                self.assertNotEqual(before_backend, self.source('backend'))
                self.assertEqual(before_dac, self.source('dac'))

    def test_unrelated_frontend_changes_do_not_block_either_java_build(self):
        before_backend, before_dac = self.source('backend'), self.source('dac')
        self.write('yudao-ui/package.json', '{"version":"2"}')
        self.assertEqual(before_backend, self.source('backend'))
        self.assertEqual(before_dac, self.source('dac'))

    def test_shared_toolchain_inputs_still_block_both_builds(self):
        for path in ('.mvn/maven.config', 'scripts/worktree-toolchain.ps1'):
            with self.subTest(path=path):
                before_backend, before_dac = self.source('backend'), self.source('dac')
                self.write(path, 'changed')
                self.assertNotEqual(before_backend, self.source('backend'))
                self.assertNotEqual(before_dac, self.source('dac'))

    def test_new_nested_maven_module_and_source_changes_are_detected(self):
        before = self.source('backend')
        self.write('platform/pom.xml', '<project><modules><module>api</module></modules></project>')
        self.write('platform/api/pom.xml', '<project/>')
        self.write('platform/api/src/main/java/Fact.java', 'v1')
        self.assertNotEqual(before, self.source('backend'))
        before = self.source('backend')
        self.write('platform/api/src/main/java/Fact.java', 'v2')
        self.assertNotEqual(before, self.source('backend'))

    def test_unknown_target_and_missing_declared_module_fail_closed(self):
        with self.assertRaises(ValueError):
            self.source('unknown')
        self.write('pom.xml', '<project><modules><module>missing</module></modules></project>')
        with self.assertRaises(FileNotFoundError):
            self.source('backend')

    def test_external_release_directory_preserves_workspace_and_source_identity(self):
        with tempfile.TemporaryDirectory() as output:
            jar = self.root / 'candidate.jar'
            jar.write_bytes(b'isolated-dac-artifact')
            revision = self.source('dac')
            with patch.object(artifact.subprocess, 'run'):
                artifact.freeze(self.root, 'dac', jar, revision, release_root=Path(output))
            manifests = list(Path(output).glob('*/manifest.json'))
            self.assertEqual(1, len(manifests))
            manifest = json.loads(manifests[0].read_text())
            self.assertEqual(self.root, Path(manifest['workspace']))
            self.assertEqual(revision, manifest['source'])
            self.assertEqual(hashlib.sha256(jar.read_bytes()).hexdigest(), manifest['sha256'])
            self.assertEqual(jar.read_bytes(), (manifests[0].parent / manifest['artifact']).read_bytes())
            self.assertFalse((self.root / '.run/build/releases').exists())

    def test_relative_release_directory_is_rejected_before_verification(self):
        with patch.object(artifact.subprocess, 'run') as verifier:
            with self.assertRaises(ValueError):
                artifact.freeze(self.root, 'dac', self.root / 'candidate.jar', 'revision', release_root=Path('relative'))
            verifier.assert_not_called()


if __name__ == '__main__':
    unittest.main()
