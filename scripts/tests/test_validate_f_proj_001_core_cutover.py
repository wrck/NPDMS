from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from validate_f_proj_001_core_cutover import REQUIRED_TABLES, validate_repository


class FProj001CoreCutoverTest(unittest.TestCase):

    def test_current_repository_satisfies_core_cutover_contract(self) -> None:
        repository = Path(__file__).resolve().parents[2]

        self.assertEqual([], validate_repository(repository))

    def test_rejects_destructive_or_incomplete_migration(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            migration = repository / "sql/migrations/V51__f_proj_001_core_project_write_model.sql"
            migration.parent.mkdir(parents=True)
            migration.write_text(
                "CREATE TABLE proj_project (id BIGINT);\nDROP TABLE pms_project;\n",
                encoding="utf-8",
            )

            errors = validate_repository(repository)

        self.assertIn("V51 must preserve data: DROP TABLE is forbidden", errors)
        for table in REQUIRED_TABLES[1:]:
            self.assertIn(f"missing formal table: {table}", errors)

    def test_rejects_legacy_project_write_mapping(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            migration = repository / "sql/migrations/V51__f_proj_001_core_project_write_model.sql"
            migration.parent.mkdir(parents=True)
            migration.write_text(
                "\n".join(f"CREATE TABLE {table} (id BIGINT);" for table in REQUIRED_TABLES),
                encoding="utf-8",
            )
            legacy = repository / "pms-module-project/src/main/java/LegacyProjectDO.java"
            legacy.parent.mkdir(parents=True)
            legacy.write_text('@TableName("pms_project")\nclass LegacyProjectDO {}\n', encoding="utf-8")

            errors = validate_repository(repository)

        self.assertEqual(
            ["legacy project write reference: pms-module-project/src/main/java/LegacyProjectDO.java"],
            errors,
        )


if __name__ == "__main__":
    unittest.main()
