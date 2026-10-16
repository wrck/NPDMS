import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('worktree_artifact', Path(__file__).with_name('worktree-artifact.py'))
artifact = importlib.util.module_from_spec(spec)
spec.loader.exec_module(artifact)


class RunningBackendArtifactTest(unittest.TestCase):
    def test_missing_or_stopped_backend_does_not_prevent_packaging(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            self.assertIsNone(artifact.running_backend_artifact(root))
            (root / '.run').mkdir()
            (root / '.run/backend-domain-test.pid').write_text('12345')
            with patch.object(artifact.subprocess, 'check_output', return_value=''):
                self.assertIsNone(artifact.running_backend_artifact(root))

    def test_running_artifact_must_remain_in_the_current_worktree(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / '.run').mkdir()
            (root / '.run/backend-domain-test.pid').write_text('12345')
            expected = root / '.run/current.jar'
            with patch.object(artifact.subprocess, 'check_output', return_value=str(expected)):
                self.assertEqual(expected, artifact.running_backend_artifact(root))
            with patch.object(artifact.subprocess, 'check_output', return_value=str(root.parent / 'other.jar')):
                with self.assertRaisesRegex(ValueError, 'Unexpected runtime location'):
                    artifact.running_backend_artifact(root)

    def test_invalid_pid_is_rejected_before_process_lookup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / '.run').mkdir()
            (root / '.run/backend-domain-test.pid').write_text('123; invalid')
            with patch.object(artifact.subprocess, 'check_output') as lookup:
                with self.assertRaisesRegex(ValueError, 'Invalid running PID'):
                    artifact.running_backend_artifact(root)
                lookup.assert_not_called()


if __name__ == '__main__':
    unittest.main()
