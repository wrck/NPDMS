"""Failure checks for lineage selection and stale/generated SQL detection."""
import json
import hashlib
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_baseline_migrations as baseline


class BaselineMigrationsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        path = self.root / baseline.BASELINE
        path.parent.mkdir(parents=True)
        path.write_bytes(b"CREATE TABLE current_table(id BIGINT);\r\n")
        (path.parent.parent / "source-manifest.json").write_text(json.dumps({
            "migrationSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "excludedTables": {"old_retired": "Retired"}, "excludedData": ["test fixtures"]}))
        self.source = self.root / "sql/migrations"
        self.source.mkdir()
        (self.source / "V1__old.sql").write_text("CREATE TABLE old_retired(id BIGINT);")
        (self.source / "V531__forward.sql").write_bytes(b"SELECT 1;\r\n")
        (self.source / "V512__fixture.sql").write_text("INSERT INTO fixtures VALUES(1);")

    def test_excludes_old_history_and_fixture_preserving_forward_bytes(self):
        files = baseline.collect(self.root)
        self.assertEqual({Path(baseline.BASELINE).name, "V531__forward.sql", "manifest.json"}, set(files))
        self.assertEqual(b"SELECT 1;\r\n", files["V531__forward.sql"])

    def test_duplicate_normalized_versions_fail(self):
        (self.source / "V531_0__duplicate.sql").write_text("SELECT 2;")
        with self.assertRaisesRegex(ValueError, "Duplicate version"):
            baseline.collect(self.root)

    def test_retired_table_reintroduction_fails(self):
        (self.source / "V532__bad.sql").write_text("CREATE TABLE IF NOT EXISTS `old_retired` (id INT);")
        with self.assertRaisesRegex(ValueError, "Retired object"):
            baseline.collect(self.root)

    def test_stale_and_unexpected_files_fail_without_deletion(self):
        output = self.root / "output"
        files = baseline.collect(self.root)
        baseline.materialize(files, output)
        baseline.materialize(files, output, check=True)
        (output / "V531__forward.sql").write_text("SELECT 9;")
        with self.assertRaisesRegex(ValueError, "stale"):
            baseline.materialize(files, output, check=True)
        extra = output / "V512__unexpected.sql"
        extra.write_text("SELECT 3;")
        with self.assertRaisesRegex(ValueError, "Unexpected"):
            baseline.materialize(files, output)
        self.assertTrue(extra.exists())

    def test_changed_frozen_baseline_fails(self):
        (self.root / baseline.BASELINE).write_text("SELECT 9;")
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            baseline.collect(self.root)


if __name__ == "__main__":
    unittest.main()
