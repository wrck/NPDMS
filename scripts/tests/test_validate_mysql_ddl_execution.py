from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_mysql_ddl_execution.py"
SPEC = importlib.util.spec_from_file_location("validate_mysql_ddl_execution", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class MysqlDdlExecutionEvidenceTest(unittest.TestCase):
    def test_parse_metrics_preserves_mysql_constraint_types(self) -> None:
        metrics = MODULE.parse_metrics(
            "8.4.10\n49\t1048\nCHECK\t79\nFOREIGN KEY\t46\nPRIMARY KEY\t49\nUNIQUE\t100\n"
        )
        self.assertEqual("8.4.10", metrics["mysqlVersion"])
        self.assertEqual(49, metrics["tableCount"])
        self.assertEqual(1048, metrics["columnCount"])
        self.assertEqual(274, metrics["constraintCount"])
        self.assertEqual(46, metrics["constraintCountByType"]["FOREIGN KEY"])

    def test_parse_metrics_rejects_incomplete_output(self) -> None:
        with self.assertRaisesRegex(ValueError, "unexpected MySQL verification output"):
            MODULE.parse_metrics("8.4.10\n")

    def test_expected_table_count_ignores_comments_and_counts_statements(self) -> None:
        ddl = "-- CREATE TABLE ignored (id BIGINT);\nCREATE TABLE first (id BIGINT);\n  CREATE TABLE second (id BIGINT);"
        self.assertEqual(2, MODULE.expected_table_count(ddl))


if __name__ == "__main__":
    unittest.main()
