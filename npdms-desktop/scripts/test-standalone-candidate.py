"""Preparation failure tests; these do not simulate Windows installation."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('candidate', Path(__file__).with_name('prepare-standalone-candidate.py'))
candidate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(candidate)


class PreparationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.names = ['SdpDac.exe', 'SdpDacHost.exe', 'SdpDacService.exe', 'SdpDacService.xml',
                      'runtime/bin/java.exe', 'app/device-ops-server.jar', 'parser-release/manifest.json']
        files = []
        for name in self.names:
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(b'fixture')
            files.append({'path': name, 'bytes': p.stat().st_size, 'sha256': candidate.digest(p)})
        self.record = {'candidateOnly': True, 'releaseFrozen': False, 'files': files}
        self.write_record()

    def write_record(self):
        (self.root / 'candidate-inputs.json').write_text(json.dumps(self.record))

    def test_complete_inventory(self):
        self.assertEqual(candidate.verify_native(self.root), self.record)

    def test_same_size_tampering_rejected(self):
        (self.root / self.names[0]).write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'payload changed'):
            candidate.verify_native(self.root)

    def test_extra_file_rejected(self):
        (self.root / 'unexpected.dll').write_bytes(b'extra')
        with self.assertRaisesRegex(ValueError, 'Unrecorded'):
            candidate.verify_native(self.root)

    def test_missing_required_component_rejected_even_if_inventory_matches(self):
        (self.root / self.names[0]).unlink()
        self.record['files'].pop(0)
        self.write_record()
        with self.assertRaisesRegex(ValueError, 'Missing native'):
            candidate.verify_native(self.root)

    def test_links_rejected(self):
        p = self.root / self.names[0]
        p.unlink()
        p.symlink_to(self.root / self.names[1])
        with self.assertRaisesRegex(ValueError, 'payload changed'):
            candidate.verify_native(self.root)

    def test_unsafe_path_rejected(self):
        for path in ['../outside', '/outside', 'C:/outside', 'folder\\outside']:
            self.record['files'][0]['path'] = path
            self.write_record()
            with self.assertRaisesRegex(ValueError, 'Unsafe'):
                candidate.verify_native(self.root)

    def test_source_drift_rejected(self):
        with self.assertRaisesRegex(ValueError, 'baseline'):
            candidate.derive('different installer')

    def test_original_service_lifecycle_preserved_verbatim(self):
        source = (Path(__file__).resolve().parents[2] / 'device-ops-platform/packaging/windows/Package.iss').read_text()
        derived = candidate.derive(source)
        lifecycle = source[source.index('procedure InitializeService();'):]
        self.assertTrue(derived.endswith(lifecycle))
        self.assertNotIn('[UninstallDelete]', derived)
        self.assertEqual(derived.count('if not CandidateFreshState() then begin'), 2)


if __name__ == '__main__':
    unittest.main()
