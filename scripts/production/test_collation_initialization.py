import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from build_collation_initialization import build


class CollationInitializationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        (self.source / "migrations").mkdir(parents=True)
        self.sql = b"""CREATE TABLE `sample` (
  `id` bigint NOT NULL,
  `name` varchar(40) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'literal utf8mb3_bin',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
INSERT INTO `sample` VALUES (1,'utf8mb3_bin');
"""
        (self.source / "migrations/B530__sample.sql").write_bytes(self.sql)
        manifest = {"migrationFile": "B530__sample.sql", "migrationSha256": hashlib.sha256(self.sql).hexdigest(),
                    "tables": {"sample": {}}, "structuralNormalization": []}
        (self.source / "source-manifest.json").write_text(json.dumps(manifest), encoding="utf8")
        self.policy = {"tables": [{"table": "sample", "targetCollation": "utf8mb4_unicode_ci"}],
                       "columns": [{"table": "sample", "column": "name", "type": "varchar(40)",
                                    "sourceCollation": "utf8mb3_general_ci", "targetCollation": "utf8mb4_unicode_ci"}]}
        self.policy_path = self.root / "policy.json"
        self.output = self.root / "new-release"

    def run_build(self):
        self.policy_path.write_text(json.dumps(self.policy), encoding="utf8")
        return build(self.source, self.policy_path, self.output)

    def test_values_comments_and_constraints_are_preserved(self):
        result = self.run_build()
        output = next((self.output / "migrations").glob("*.sql")).read_bytes()
        self.assertIn(b"COMMENT 'literal utf8mb3_bin'", output)
        self.assertIn(b"INSERT INTO `sample` VALUES (1,'utf8mb3_bin');", output)
        self.assertIn(b"PRIMARY KEY (`id`)", output)
        self.assertEqual(result["changedColumns"], 1)

    def test_tampered_source_is_rejected(self):
        (self.source / "migrations/B530__sample.sql").write_bytes(self.sql + b"-- drift")
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.run_build()
        self.assertFalse(self.output.exists())

    def test_missing_character_policy_is_rejected(self):
        self.policy["columns"] = []
        with self.assertRaisesRegex(ValueError, "Missing character policy"):
            self.run_build()
        self.assertFalse(self.output.exists())

    def test_frozen_output_cannot_be_overwritten(self):
        self.run_build()
        with self.assertRaisesRegex(ValueError, "overwrite"):
            self.run_build()


if __name__ == "__main__":
    unittest.main()
