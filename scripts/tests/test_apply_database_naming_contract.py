from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "apply_database_naming_contract.py"
SPEC = importlib.util.spec_from_file_location("apply_database_naming_contract", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ApplyDatabaseNamingContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.contract = {
            "tables": [
                {"source": "pms_order_contract_rel", "target": "com_order_contract_relation", "owner": "COM"},
                {"source": "pms_project_deliverable", "target": "acc_project_deliverable", "owner": "ACC"},
            ],
            "fields": [
                {"sourceTable": "pms_project_deliverable", "sourceColumn": "submitted_time", "targetTable": "acc_project_deliverable", "targetColumn": "submit_time"}
            ],
        }
        self.ddl = """CREATE TABLE pms_project_deliverable (
  submitted_time DATETIME COMMENT 'submitted_time and pms_project_deliverable source text',
  CONSTRAINT fk_rel FOREIGN KEY (id) REFERENCES pms_order_contract_rel (id)
) ENGINE = InnoDB COMMENT = 'pms_project_deliverable';"""

    def test_transforms_identifiers_but_preserves_comments(self) -> None:
        result = MODULE.transform_ddl(self.ddl, self.contract)
        self.assertIn("CREATE TABLE acc_project_deliverable", result)
        self.assertIn("submit_time DATETIME", result)
        self.assertIn("REFERENCES com_order_contract_relation", result)
        self.assertIn("'submitted_time and pms_project_deliverable source text'", result)
        self.assertIn("COMMENT = 'pms_project_deliverable'", result)

    def test_transform_is_idempotent(self) -> None:
        once = MODULE.transform_ddl(self.ddl, self.contract)
        self.assertEqual(once, MODULE.transform_ddl(once, self.contract))

    def test_does_not_replace_partial_identifier(self) -> None:
        ddl = "CREATE TABLE pms_order_contract_rel_archive (id BIGINT) ENGINE = InnoDB;"
        self.assertEqual(ddl, MODULE.transform_ddl(ddl, self.contract))


if __name__ == "__main__":
    unittest.main()
