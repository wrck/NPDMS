from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


SCRIPTS = Path(__file__).resolve().parents[1]
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))

from validate_f_proj_001_schema import (
    FORBIDDEN_TABLES,
    REQUIRED_TABLES,
    parse_tables,
    parse_v60_columns,
    validate_repository,
)


class FProj001SchemaTest(unittest.TestCase):

    def test_current_repository_has_atomic_schema_carriers(self) -> None:
        repository = Path(__file__).resolve().parents[2]

        self.assertEqual([], validate_repository(repository))

    def test_parser_requires_atomic_carriers_and_rejects_draft(self) -> None:
        sql = "\n".join(f"CREATE TABLE {table} (id BIGINT);" for table in REQUIRED_TABLES)
        tables = parse_tables(sql + "\nCREATE TABLE proj_project_creation_draft (id BIGINT);")

        self.assertEqual(set(), REQUIRED_TABLES - tables)
        self.assertEqual({"proj_project_creation_draft"}, FORBIDDEN_TABLES & tables)

    def test_missing_v60_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            errors = validate_repository(Path(directory))

        self.assertEqual(
            ["missing migration: sql/migrations/V60__f_proj_001_manual_project_creation.sql"],
            errors,
        )

    def test_parser_keeps_altered_columns_on_their_own_table(self) -> None:
        columns = parse_v60_columns(
            "ALTER TABLE proj_project ADD COLUMN current_stage_code VARCHAR(32);\n"
            "ALTER TABLE proj_project_template_revision "
            "CHANGE COLUMN code template_code VARCHAR(64);"
        )

        self.assertEqual({"current_stage_code"}, columns["proj_project"])
        self.assertEqual({"template_code"}, columns["proj_project_template_revision"])


if __name__ == "__main__":
    unittest.main()
